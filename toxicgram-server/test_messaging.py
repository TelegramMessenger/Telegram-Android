import os
import tempfile
import unittest
from pathlib import Path

from fastapi.testclient import TestClient
import server

class MessagingTest(unittest.TestCase):
    def test_private_messages_and_settings(self):
        with tempfile.TemporaryDirectory() as temp:
            server.DB = str(Path(temp) / "test.sqlite3")
            server.PEPPER = "test-only-key-not-for-deployment-0000000"
            server.TOKEN = ""
            with TestClient(server.app) as client:
                def account(tid):
                    n,code=server.issue(tid)
                    res=client.post("/v1/auth/verify",json={"number":n,"code":code})
                    self.assertEqual(res.status_code,200)
                    return n,{"Authorization":"Bearer "+res.json()["access_token"]}
                a,ha=account(1); b,hb=account(2); c,hc=account(3)
                self.assertEqual(client.get("/v1/chats").status_code,401)
                sent=client.post("/v1/messages",headers=ha,json={"recipient":b,"text":"hello"})
                self.assertEqual(sent.status_code,200); mid=sent.json()["id"]
                self.assertEqual(client.get("/v1/chats",headers=hb).json()["chats"][0]["unread"],1)
                self.assertEqual(client.get("/v1/chats/"+a+"/messages",headers=hc).json()["messages"],[])
                self.assertEqual(client.put(f"/v1/messages/{mid}",headers=hc,json={"text":"bad"}).status_code,404)
                self.assertEqual(client.put(f"/v1/messages/{mid}",headers=hb,json={"text":"bad"}).status_code,403)
                self.assertEqual(client.put(f"/v1/messages/{mid}",headers=ha,json={"text":"edited"}).status_code,200)
                history=client.get("/v1/chats/"+a+"/messages",headers=hb).json()["messages"]
                self.assertEqual(history[0]["text"],"edited");self.assertGreater(history[0]["edited"],0)
                self.assertEqual(client.put("/v1/chats/"+a+"/read",headers=hb,json={"last_id":mid}).status_code,200)
                self.assertEqual(client.get("/v1/chats",headers=hb).json()["chats"][0]["unread"],0)
                self.assertEqual(client.put("/v1/chats/"+a+"/settings",headers=hb,json={"pinned":True,"archived":True}).status_code,200)
                self.assertEqual(client.get("/v1/chats",headers=hb).json()["chats"][0]["archived"],1)
                self.assertEqual(client.get("/v1/chats",headers=ha).json()["chats"][0]["archived"],0)
                self.assertEqual(client.delete(f"/v1/messages/{mid}?everyone=true",headers=hb).status_code,403)
                self.assertEqual(client.delete(f"/v1/messages/{mid}",headers=hb).status_code,200)
                self.assertEqual(client.get("/v1/chats/"+a+"/messages",headers=hb).json()["messages"],[])
                self.assertEqual(len(client.get("/v1/chats/"+b+"/messages",headers=ha).json()["messages"]),1)
                self.assertEqual(client.delete(f"/v1/messages/{mid}?everyone=true",headers=ha).status_code,200)
                self.assertEqual(client.get("/v1/chats/"+b+"/messages",headers=ha).json()["messages"],[])
                self.assertEqual(client.post("/v1/messages",headers=ha,json={"recipient":a,"text":"note"}).status_code,200)
                self.assertEqual(client.get("/v1/chats",headers=ha).json()["chats"][0]["unread"],0)

    def test_phone_profiles_rooms_and_reactions(self):
        with tempfile.TemporaryDirectory() as temp:
            server.DB=str(Path(temp)/"test.sqlite3");server.PEPPER="test-only-key-not-for-deployment-0000000";server.TOKEN=""
            with TestClient(server.app) as client:
                def register(tid,prefix):
                    n,code=server.issue(tid)
                    phone=server.phone_for(n,prefix)
                    result=client.post("/v1/auth/verify",json={"number":phone,"code":code})
                    self.assertEqual(result.status_code,200)
                    self.assertEqual(result.json()["number"],n)
                    self.assertEqual(result.json()["phone"],phone)
                    return n,phone,{"Authorization":"Bearer "+result.json()["access_token"]}
                a,pa,ha=register(11,"+7");b,pb,hb=register(12,"+1");c,pc,hc=register(13,"+388")
                self.assertTrue(pa.startswith("+7000"));self.assertTrue(pb.startswith("+1000"));self.assertTrue(pc.startswith("+388000"))
                self.assertEqual(client.put("/v1/profile",headers=ha,json={"name":"Alice","username":"alice_test","bio":"Hello"}).status_code,200)
                self.assertEqual(client.get("/v1/users/@alice_test",headers=hb).json()["number"],a)
                self.assertEqual(client.put("/v1/profile",headers=hb,json={"name":"Bob","username":"ALICE_TEST"}).status_code,409)
                new=server.phone_for(a,"+388")
                self.assertNotEqual(pa,new)
                self.assertEqual(client.get("/v1/users/"+pa,headers=hb).status_code,404)
                self.assertEqual(client.get("/v1/users/"+new,headers=hb).json()["number"],a)
                mid=client.post("/v1/messages",headers=ha,json={"recipient":pb,"text":"hello"}).json()["id"]
                self.assertEqual(client.put(f"/v1/messages/{mid}/reaction",headers=hc,json={"emoji":"👍"}).status_code,404)
                self.assertEqual(client.put(f"/v1/messages/{mid}/reaction",headers=hb,json={"emoji":"👍"}).status_code,200)
                self.assertEqual(client.get("/v1/chats/"+b+"/messages",headers=ha).json()["messages"][0]["reactions"][0]["count"],1)
                self.assertEqual(client.put("/v1/users/"+a+"/block",headers=hb,json={"blocked":True}).status_code,200)
                self.assertEqual(client.post("/v1/messages",headers=ha,json={"recipient":b,"text":"blocked"}).status_code,403)
                self.assertEqual(client.put("/v1/users/"+a+"/block",headers=hb,json={"blocked":False}).status_code,200)
                for kind in ("group","channel"):
                    rid=client.post("/v1/rooms",headers=ha,json={"kind":kind,"title":"Test "+kind}).json()["id"]
                    self.assertEqual(client.get(f"/v1/rooms/{rid}/messages",headers=hc).status_code,404)
                    self.assertEqual(client.post(f"/v1/rooms/{rid}/invite",headers=ha,json={"number":pb}).status_code,200)
                    self.assertEqual(client.post(f"/v1/rooms/{rid}/invite",headers=hb,json={"number":pc}).status_code,403)
                    self.assertEqual(client.post(f"/v1/rooms/{rid}/messages",headers=ha,json={"text":"owner post"}).status_code,200)
                    self.assertEqual(client.post(f"/v1/rooms/{rid}/messages",headers=hb,json={"text":"member post"}).status_code,200 if kind=="group" else 403)
                    self.assertEqual(client.get(f"/v1/rooms/{rid}/messages",headers=hb).status_code,200)
                    self.assertEqual(client.delete(f"/v1/rooms/{rid}/membership",headers=hb).status_code,200)
                    self.assertEqual(client.get(f"/v1/rooms/{rid}/messages",headers=hb).status_code,404)


    def test_attachments_gifts_stories(self):
        with tempfile.TemporaryDirectory() as temp:
            server.DB=str(Path(temp)/"test.sqlite3");server.PEPPER="test-only-key-not-for-deployment-0000000";server.TOKEN=""
            with TestClient(server.app) as client:
                def register(tid):
                    n,code=server.issue(tid)
                    res=client.post("/v1/auth/verify",json={"number":n,"code":code})
                    return n,{"Authorization":"Bearer "+res.json()["access_token"]}
                a,ha=register(21);b,hb=register(22);c,hc=register(23)
                raw=b"hello attachment"
                res=client.post("/v1/attachments",params={"recipient":b,"name":"note.txt"},headers=ha,content=raw)
                self.assertEqual(res.status_code,200);mid=res.json()["id"];aid=res.json()["attachment"]
                self.assertEqual(client.get("/v1/media/"+aid,headers=hb).content,raw)
                self.assertEqual(client.get("/v1/media/"+aid,headers=hc).status_code,404)
                self.assertEqual(client.get("/v1/media/"+aid).status_code,401)
                self.assertEqual(client.get("/v1/chats/"+a+"/messages",headers=hb).json()["messages"][0]["attachment"]["id"],aid)
                self.assertEqual(client.delete(f"/v1/messages/{mid}",headers=hb).status_code,200)
                self.assertEqual(client.get("/v1/media/"+aid,headers=hb).status_code,404)
                self.assertEqual(client.get("/v1/media/"+aid,headers=ha).status_code,200)
                self.assertEqual(client.delete(f"/v1/messages/{mid}?everyone=true",headers=ha).status_code,200)
                self.assertEqual(client.get("/v1/media/"+aid,headers=ha).status_code,404)
                self.assertEqual(client.put("/v1/avatar",headers=ha,content=raw).status_code,400)
                self.assertEqual(client.get("/v1/gifts",headers=ha).json()["balance"],1000)
                res=client.post("/v1/gifts",headers=ha,json={"recipient":b,"kind":"heart"})
                self.assertEqual(res.status_code,200)
                self.assertEqual(client.get("/v1/gifts",headers=ha).json()["balance"],950)
                self.assertEqual(client.get("/v1/gifts",headers=hb).json()["received"][0]["serial"],res.json()["serial"])
                client.post("/v1/messages",headers=ha,json={"recipient":b,"text":"hi"})
                sid=client.post("/v1/stories",headers=ha,json={"text":"story"}).json()["id"]
                self.assertEqual(len(client.get("/v1/stories",headers=hb).json()["stories"]),1)
                self.assertEqual(client.get("/v1/stories",headers=hc).json()["stories"],[])
                self.assertEqual(client.delete(f"/v1/stories/{sid}",headers=hb).status_code,404)
                self.assertEqual(client.delete(f"/v1/stories/{sid}",headers=ha).status_code,200)
                self.assertEqual(client.get("/v1/stories",headers=hb).json()["stories"],[])

if __name__=="__main__":
    unittest.main()
