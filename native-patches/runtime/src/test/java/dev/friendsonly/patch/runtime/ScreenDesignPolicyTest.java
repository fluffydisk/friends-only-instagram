package dev.friendsonly.patch.runtime;

public final class ScreenDesignPolicyTest {
    private static int checks;
    private static void verify(boolean value) { checks++; if (!value) throw new AssertionError("Design scope " + checks); }
    public static void main(String[] args) {
        verify(ScreenDesignPolicy.surface(0x7f0e149c) == ScreenDesignPolicy.FEED_HEADER);
        verify(ScreenDesignPolicy.surface(0x7f0e0415) == ScreenDesignPolicy.INBOX);
        verify(ScreenDesignPolicy.surface(0x7f0e1204) == ScreenDesignPolicy.PROFILE);
        verify(ScreenDesignPolicy.surface(0x7f0e04ca) == ScreenDesignPolicy.MESSAGE);
        verify(ScreenDesignPolicy.surface(0x7f0e13a0) == ScreenDesignPolicy.STORY);
        verify(ScreenDesignPolicy.surface(0x7f0e04cd) == ScreenDesignPolicy.THREAD_HEADER);
        for (int unrelated : new int[]{0, -1, 0x7f0e002b, 0x7f0e1497, 0x7f0e1499, 0x7f0e04cb, 0x7f0b0415}) {
            verify(ScreenDesignPolicy.surface(unrelated) == ScreenDesignPolicy.NONE);
        }
        for (String essential : new String[]{null, "", "row_feed_photo_imageview", "row_feed_button_like",
                "row_feed_button_comment", "row_feed_button_share", "row_feed_button_save", "row_feed_button_options",
                "profile_header_user_action_follow_button", "profile_header_familiar_following_value",
                "profile_header_teen_account_banner", "profile_header_request_confirm_button", "profile_header_restrict_indicator",
                "row_profile_header_direct_message", "row_thread_composer_edittext", "row_thread_composer_button_send",
                "row_thread_composer_button_voice", "row_thread_composer_button_gallery", "thread_title", "message_text", "error"}) {
            for (int surface = 0; surface <= 9; surface++) verify(!ScreenDesignPolicy.hide(surface, essential));
        }
        verify(ScreenDesignPolicy.hide(ScreenDesignPolicy.FEED_BODY, "row_feed_like_count"));
        verify(!ScreenDesignPolicy.hide(ScreenDesignPolicy.PROFILE, "row_feed_like_count"));
        verify(ScreenDesignPolicy.hide(ScreenDesignPolicy.PROFILE, "profile_header_familiar_followers_value"));
        verify(!ScreenDesignPolicy.hide(ScreenDesignPolicy.INBOX, "profile_header_familiar_followers_value"));
        verify(ScreenDesignPolicy.hide(ScreenDesignPolicy.COMPOSER, "row_thread_composer_meta_ai_discover"));
        verify(!ScreenDesignPolicy.hide(ScreenDesignPolicy.MESSAGE, "row_thread_composer_meta_ai_discover"));
        verify(!ScreenDesignPolicy.hide(ScreenDesignPolicy.COMPOSER, "row_thread_composer_meta_ai_discover_error"));
        verify(!ScreenDesignPolicy.hide(ScreenDesignPolicy.PROFILE, "other_profile_header_familiar_followers_value"));
        verify(ScreenDesignPolicy.title("profile_header_full_name"));
        verify(!ScreenDesignPolicy.title("profile_header_full_name_error"));
        verify(!ScreenDesignPolicy.title(null));
        System.out.println("ScreenDesignPolicyTest: " + checks + " assertions passed");
    }
}
