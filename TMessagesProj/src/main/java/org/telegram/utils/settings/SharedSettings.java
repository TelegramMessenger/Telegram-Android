package org.telegram.utils.settings;

import org.telegram.messenger.BuildConfig;
import org.telegram.ui.ActionBar.SimpleActionBarDoneButton;
import org.telegram.utils.recorder.round.RoundVideoSession;
import org.telegram.utils.recorder.audio.AudioRecordingSession;

public final class SharedSettings {

    public static final BooleanSetting experimentalSettingsAllowed =
        BooleanSetting.of("experimental_settings_allowed", BuildConfig.DEBUG_VERSION);

    public static final BooleanSetting audioRecorderV2Enabled =
        BooleanSetting.of("audio_recorder_v2_enabled", BuildConfig.DEBUG_VERSION);

    public static final EnumSetting<AudioRecordingSession.NoiseSuppression> audioRecorderNoiseSuppression =
        EnumSetting.of("audio_recorder_noise_suppression", AudioRecordingSession.NoiseSuppression.DISABLED);

    public static final BooleanSetting roundVideoCamera2Enabled =
        BooleanSetting.of("round_video_camera2_enabled", BuildConfig.DEBUG_VERSION);

    public static final EnumSetting<RoundVideoSession.OutputResolution> roundVideoOutputResolution =
        EnumSetting.of("round_video_output_resolution", RoundVideoSession.OutputResolution.P480);

    public static final EnumSetting<RoundVideoSession.CameraResolution> roundVideoCameraResolution =
        EnumSetting.of("round_video_camera_resolution", RoundVideoSession.CameraResolution.HIGH);

    public static final EnumSetting<RoundVideoSession.FrameRate> roundVideoFrameRate =
        EnumSetting.of("round_video_frame_rate", RoundVideoSession.FrameRate.FPS_30);

    public static final IntSetting roundVideoVideoBitrate =
        IntSetting.of("round_video_video_bitrate", 1_200_000);

    public static final BooleanSetting roundVideoComposition =
        BooleanSetting.of("round_video_composition", true);

    public static final EnumSetting<RoundVideoSession.CameraFacing> roundVideoLastCamera =
        EnumSetting.of("round_video_last_camera", RoundVideoSession.CameraFacing.FRONT);

    public static final EnumSetting<SimpleActionBarDoneButton.Style> headerDoneButtonStyle =
        EnumSetting.of("header_done_button_style", SimpleActionBarDoneButton.Style.GLASS);

    public static final EnumSetting<SimpleActionBarDoneButton.Shape> headerDoneButtonShape =
        EnumSetting.of("header_done_button_shape", SimpleActionBarDoneButton.Shape.TEXT);

    // ButterGram: ghost mode
    public static final BooleanSetting buttergramGhostRead =
        BooleanSetting.of("buttergram_ghost_read", false);

    public static final BooleanSetting buttergramGhostTyping =
        BooleanSetting.of("buttergram_ghost_typing", false);

    public static final BooleanSetting buttergramGhostOnline =
        BooleanSetting.of("buttergram_ghost_online", false);

    // ButterGram: keep messages deleted remotely
    public static final BooleanSetting buttergramSaveDeleted =
        BooleanSetting.of("buttergram_save_deleted", false);

    // ButterGram: interface customization
    public static final BooleanSetting buttergramHidePremiumRows =
        BooleanSetting.of("buttergram_hide_premium_rows", false);

    // ButterGram: deleted messages mark
    public static final BooleanSetting buttergramMarkDeleted =
        BooleanSetting.of("buttergram_mark_deleted", true);

    public static final IntSetting buttergramDeletedColor =
        IntSetting.of("buttergram_deleted_color", 0xFFE53935);

    // ButterGram: ids and exact time
    public static final BooleanSetting buttergramExactTime =
        BooleanSetting.of("buttergram_exact_time", false);

    public static final BooleanSetting buttergramShowIds =
        BooleanSetting.of("buttergram_show_ids", false);

    // ButterGram: send confirmations
    public static final BooleanSetting buttergramWarnOffensive =
        BooleanSetting.of("buttergram_warn_offensive", false);

    public static final BooleanSetting buttergramConfirmVoice =
        BooleanSetting.of("buttergram_confirm_voice", false);

    public static final BooleanSetting buttergramConfirmRound =
        BooleanSetting.of("buttergram_confirm_round", false);

    public static final BooleanSetting buttergramConfirmSticker =
        BooleanSetting.of("buttergram_confirm_sticker", false);

    // ButterGram: privacy and interface
    public static final BooleanSetting buttergramHidePhone =
        BooleanSetting.of("buttergram_hide_phone", false);

    public static final BooleanSetting buttergramHideStories =
        BooleanSetting.of("buttergram_hide_stories", false);

    public static final BooleanSetting buttergramBigFont =
        BooleanSetting.of("buttergram_big_font", false);

    // ButterGram: edit history
    public static final BooleanSetting buttergramEditHistory =
        BooleanSetting.of("buttergram_edit_history", true);

    // ButterGram: local premium (client side only)
    public static final BooleanSetting buttergramLocalPremium =
        BooleanSetting.of("buttergram_local_premium", false);

    public static final BooleanSetting buttergramHideFab =
        BooleanSetting.of("buttergram_hide_fab", false);

    public static final BooleanSetting buttergramHideSearch =
        BooleanSetting.of("buttergram_hide_search", false);

    public static final BooleanSetting buttergramCenterTitle =
        BooleanSetting.of("buttergram_center_title", false);

    public static final BooleanSetting buttergramNoTail =
        BooleanSetting.of("buttergram_no_tail", false);

    public static final BooleanSetting buttergramEditedIcon =
        BooleanSetting.of("buttergram_edited_icon", false);

    public static final BooleanSetting buttergramShowForwards =
        BooleanSetting.of("buttergram_show_forwards", false);

    public static final BooleanSetting buttergramHideSideShare =
        BooleanSetting.of("buttergram_hide_side_share", false);

    public static final BooleanSetting buttergramHideReactionsIn =
        BooleanSetting.of("buttergram_hide_reactions_in", false);

    public static final BooleanSetting buttergramHideReactionsOut =
        BooleanSetting.of("buttergram_hide_reactions_out", false);

    public static final BooleanSetting buttergramEndlessRecents =
        BooleanSetting.of("buttergram_endless_recents", false);

    public static final BooleanSetting buttergramForceSnow =
        BooleanSetting.of("buttergram_force_snow", false);

    public static final BooleanSetting buttergramHideMenuReply =
        BooleanSetting.of("buttergram_hide_menu_reply", false);

    public static final BooleanSetting buttergramHideMenuForward =
        BooleanSetting.of("buttergram_hide_menu_forward", false);

    public static final BooleanSetting buttergramHideMenuCopy =
        BooleanSetting.of("buttergram_hide_menu_copy", false);

    public static final BooleanSetting buttergramHideMenuCopyLink =
        BooleanSetting.of("buttergram_hide_menu_copy_link", false);

    public static final BooleanSetting buttergramHideMenuShare =
        BooleanSetting.of("buttergram_hide_menu_share", false);

    public static final BooleanSetting buttergramHideMenuReport =
        BooleanSetting.of("buttergram_hide_menu_report", false);

    public static final BooleanSetting buttergramHideMenuSave =
        BooleanSetting.of("buttergram_hide_menu_save", false);

    public static final BooleanSetting buttergramHideMenuTranslate =
        BooleanSetting.of("buttergram_hide_menu_translate", false);

    public static final BooleanSetting buttergramHideMenuPin =
        BooleanSetting.of("buttergram_hide_menu_pin", false);

    public static final StringSetting buttergramCustomTitle =
        StringSetting.of("buttergram_custom_title", "");

    public static final IntSetting buttergramAvatarRadius =
        IntSetting.of("buttergram_avatar_radius", 100);

    public static final IntSetting buttergramStickerScale =
        IntSetting.of("buttergram_sticker_scale", 100);

    public static final BooleanSetting buttergramScreenshotEditor =
        BooleanSetting.of("buttergram_screenshot_editor", true);

    public static final BooleanSetting buttergramChatCenterTitle =
        BooleanSetting.of("buttergram_chat_center_title", true);

    public static final BooleanSetting buttergramBackBadge =
        BooleanSetting.of("buttergram_back_badge", true);

    public static final BooleanSetting buttergramRememberKeyboard =
        BooleanSetting.of("buttergram_remember_keyboard", true);

    public static final BooleanSetting buttergramIosInput =
        BooleanSetting.of("buttergram_ios_input", true);

    public static final BooleanSetting buttergramProfileGifts =
        BooleanSetting.of("buttergram_profile_gifts", true);

    public static final BooleanSetting buttergramProfileFullBg =
        BooleanSetting.of("buttergram_profile_full_bg", true);

    // seconds the text message is held back with an "Undo" button, 0 = off
    public static final IntSetting buttergramUndoSend =
        IntSetting.of("buttergram_undo_send", 0);

    // 0 reply, 1 forward, 2 copy text, 3 nothing
    public static final IntSetting buttergramSwipeAction =
        IntSetting.of("buttergram_swipe_action", 0);

    private SharedSettings() {
    }
}
