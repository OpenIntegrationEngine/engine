package com.mirth.connect.client.ui;

import javax.swing.ImageIcon;

public class BrandingConstants {

    public static final String PRODUCT_NAME = "Open Integration Engine";
    public static final String COMPANY_NAME = "Open Integration Engine";

    /*
        Sets the title of the Administrator window
     */
    public static final String WINDOW_TITLE = "Open Integration Engine Administrator";

    public static final String ISSUE_TRACKER_LOCATION = "https://github.com/openintegrationengine/engine/issues";


    // The URL that is opened when clicking the bottom image in Login window
    // The one where you're asked for server URL, username, and password
    public static final String COMPANY_URL = "https://openintegrationengine.org";
    public static final String COMPANY_TOOLTIP = "Open Integration Engine";


    // The URL that is opened when clicking the image in the Top right corner of the main administrator window
    // The URL that is opened when clicking "Visit + PRODUCT_NAME" button in Administrator
    public static final String PRODUCT_URL = "https://github.com/openintegrationengine/engine";
    public static final String PRODUCT_TOOLTIP = "Open Integration Engine";


    // The URL that is opened when clicking "Help" button in Administrator
    public static String HELP_URL_LOCATION = "https://github.com/OpenIntegrationEngine/engine/discussions";

    // The "More info" in Server settings "Provide usage statistics"
    public static final String PRIVACY_URL = "https://github.com/openintegrationengine";
    public static final String PRIVACY_TOOLTIP = "Privacy Information";

    // Icons
    // Favicon must be at 32px x 32px scale
    public static final ImageIcon FAVICON = new ImageIcon(com.mirth.connect.client.ui.Frame.class.getResource("images/branding/oie_logo_only_white_background_32x32.png"));
    // These images must be at 215px x 30px scale
    public static final ImageIcon LOGO = new ImageIcon(com.mirth.connect.client.ui.Frame.class.getResource("images/branding/oie_logo_banner_text_215x30.png"));
    public static final ImageIcon LOGO_GRAY = new ImageIcon(com.mirth.connect.client.ui.Frame.class.getResource("images/branding/oie_white_logo_banner_text_215x30.png"));
}
