package dev.friendsonly.patch.runtime;

/** Exact version-pinned allowlist, never substring matching against text or account content. */
public final class ScreenDesignPolicy {
    public static final int NONE = 0, FEED_HEADER = 1, FEED_BODY = 2, INBOX = 3,
            MESSAGE = 4, COMPOSER = 5, PROFILE = 6, STORY = 7, THREAD_HEADER = 8, INBOX_DETAILS = 9;
    private ScreenDesignPolicy() {}

    public static int surface(int layout) {
        switch (layout) {
            case 0x7f0e149c: return FEED_HEADER; // row_feed_media_profile_header
            case 0x7f0e1498: return FEED_BODY; // row_feed_media_media_group
            case 0x7f0e148f: return FEED_BODY; // row_feed_comment
            case 0x7f0e14ad: return FEED_BODY; // row_feed_view_all_comments
            case 0x7f0e0415: return INBOX;
            case 0x7f0e0416: return INBOX_DETAILS;
            case 0x7f0e04c9: return MESSAGE;
            case 0x7f0e04ca: return MESSAGE;
            case 0x7f0e039f: return COMPOSER;
            case 0x7f0e03a0: return COMPOSER;
            case 0x7f0e1202: case 0x7f0e1203: case 0x7f0e1204: return PROFILE;
            case 0x7f0e11ff: return PROFILE;
            case 0x7f0e13a0: case 0x7f0e13a4: case 0x7f0e13a5: return STORY;
            case 0x7f0e04cd: return THREAD_HEADER;
            default: return NONE;
        }
    }

    public static boolean hide(int surface, String name) {
        if (name == null) return false;
        if (surface == FEED_HEADER || surface == FEED_BODY) {
            return "row_feed_like_count".equals(name) || "row_feed_share_count".equals(name)
                    || "row_feed_button_insights".equals(name) || "row_feed_button_repost".equals(name);
        }
        if (surface == PROFILE) {
            return "profile_header_barcelona_app_switcher_button".equals(name)
                    || "profile_header_familiar_followers_value".equals(name)
                    || "profile_header_familiar_followers_label".equals(name)
                    || "profile_header_familiar_post_count_value".equals(name)
                    || "profile_header_familiar_post_count_label".equals(name)
                    || "profile_header_followers_stacked_familiar".equals(name)
                    || "profile_header_post_count_front_familiar".equals(name)
                    || "profile_header_name_and_bio_upsell".equals(name)
                    || "profile_header_profile_card_nux_banner".equals(name)
                    || "row_profile_header_button_chaining".equals(name);
        }
        if (surface == COMPOSER) {
            return "direct_composer_meta_ai_composer_upsell".equals(name)
                    || "row_thread_composer_meta_ai_discover".equals(name)
                    || "row_thread_composer_meta_ai_invocation".equals(name)
                    || "row_thread_composer_imagine_canvas".equals(name)
                    || "row_thread_composer_imagine_intent".equals(name)
                    || "row_thread_right_composer_button_write_with_ai".equals(name);
        }
        return false;
    }

    public static boolean title(String name) {
        return "row_feed_photo_profile_name".equals(name) || "profile_header_full_name".equals(name)
                || "profile_header_full_name_above_vanity".equals(name) || "thread_title".equals(name);
    }
}
