package com.keepsake.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.webkit.MimeTypeMap;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.ScaleGestureDetector;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_IMPORT_PHOTO = 1;
    private static final int REQUEST_IMPORT_VIDEO = 2;
    private static final int REQUEST_CAPTURE_PHOTO = 3;
    private static final int REQUEST_CAPTURE_VIDEO = 4;
    private static final String STATE_PENDING_URI = "pending_uri";
    private static final String STATE_PENDING_TYPE = "pending_type";
    private static final String COLOR_BLUSH = "#F8E9E5";

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private PostStore store;
    private LinearLayout feed;
    private LinearLayout screenContent;
    private LinearLayout navigation;
    private ScrollView scrollContainer;
    private FlowerView backgroundFlower;
    private LinearLayout albumHighlightRow;
    private Spinner memoryAlbumFilter;
    private EditText searchField;
    private int selectedTab;
    private int sortOrder;
    private long albumFilterId = -1;
    private long dateFilter = -1;
    private Uri pendingUri;
    private String pendingMediaType;
    private OnBackInvokedCallback backInvokedCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new PostStore(this);
        if (savedInstanceState != null) {
            String savedUri = savedInstanceState.getString(STATE_PENDING_URI);
            pendingUri = savedUri == null ? null : Uri.parse(savedUri);
            pendingMediaType = savedInstanceState.getString(STATE_PENDING_TYPE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backInvokedCallback = this::handleBackPressed;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, backInvokedCallback);
        }
        buildScreen();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (pendingUri != null) {
            outState.putString(STATE_PENDING_URI, pendingUri.toString());
            outState.putString(STATE_PENDING_TYPE, pendingMediaType);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backInvokedCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backInvokedCallback);
            backInvokedCallback = null;
        }
        ioExecutor.shutdown();
        store.close();
        super.onDestroy();
    }

    private void buildScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(color(COLOR_BLUSH));
        backgroundFlower = new FlowerView(this);
        backgroundFlower.setVariant(3);
        backgroundFlower.setAlpha(.12f);
        backgroundFlower.setClickable(false);
        backgroundFlower.setFocusable(false);
        backgroundFlower.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(backgroundFlower, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.TRANSPARENT);
        getWindow().setStatusBarColor(color(COLOR_BLUSH));
        getWindow().setNavigationBarColor(color(COLOR_BLUSH));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }

        scrollContainer = new ScrollView(this);
        scrollContainer.setFillViewport(true);
        scrollContainer.setClipToPadding(false);
        screenContent = new LinearLayout(this);
        screenContent.setOrientation(LinearLayout.VERTICAL);
        screenContent.setPadding(dp(20), dp(13), dp(20), dp(24));
        scrollContainer.addView(screenContent);
        page.addView(scrollContainer, new LinearLayout.LayoutParams(-1, 0, 1));
        navigation = buildBottomNavigation();
        page.addView(navigation);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset;
            int bottomInset;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets systemBars = insets.getInsets(WindowInsets.Type.systemBars());
                topInset = systemBars.top;
                bottomInset = systemBars.bottom;
            } else {
                topInset = insets.getSystemWindowInsetTop();
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            page.setPadding(0, topInset, 0, 0);
            navigation.setPadding(0, 0, 0, bottomInset);
            ViewGroup.LayoutParams params = navigation.getLayoutParams();
            params.height = dp(64) + bottomInset;
            navigation.setLayoutParams(params);
            return insets;
        });
        setContentView(root);
        renderHome();
    }

    private View buildHeader(boolean showMark) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        if (!showMark) {
            header.addView(new View(this), new LinearLayout.LayoutParams(0, dp(42), 1));
            TextView info = label("i", 17, "#354A3C", true);
            info.setGravity(Gravity.CENTER);
            GradientDrawable infoBackground = background("#FBF1ED", 50);
            infoBackground.setStroke(dp(1), color("#E9CCC4"));
            info.setBackground(infoBackground);
            info.setContentDescription("Privacy and app information");
            info.setOnClickListener(view -> showAppInformation());
            header.addView(info, new LinearLayout.LayoutParams(dp(38), dp(38)));
        } else {
            ImageView mark = new ImageView(this);
            mark.setImageResource(R.drawable.ic_launcher);
            mark.setScaleType(ImageView.ScaleType.FIT_XY);
            mark.setBackground(background("#31483B", 12));
            mark.setClipToOutline(true);
            mark.setContentDescription("Keepsake app icon");
            header.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(42)));

            LinearLayout brand = new LinearLayout(this);
            brand.setOrientation(LinearLayout.VERTICAL);
            brand.setPadding(dp(10), 0, 0, 0);
            brand.addView(label("Keepsake", 19, "#2E3B32", true));
            brand.addView(label("A QUIET PLACE FOR YOUR STORY", 9, "#8D796F", true), marginTop(2));
            header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        }

        header.setPadding(0, 0, 0, dp(4));
        return header;
    }

    private void showAppInformation() {
        LinearLayout sections = new LinearLayout(this);
        sections.setOrientation(LinearLayout.VERTICAL);
        sections.setPadding(dp(18), dp(8), dp(18), dp(18));
        sections.addView(label(
                "Keepsake is a private, offline photo journal. Your memories stay on this device.",
                13, "#4E4741", false));
        sections.addView(label("PRIVACY POLICY", 9, "#8D625C", true), marginTop(18));
        addPolicyBlock(sections, "What stays on your device",
                "Photos and videos you save, captions, comments, album names, and related dates are stored locally in Keepsake's private app storage. Keepsake does not require an account and does not send this content to a Keepsake server.");
        addPolicyBlock(sections, "What Keepsake collects",
                "This app has no internet permission and includes no advertising, analytics, or cross-app tracking. The publisher does not receive your saved memories or comments through the app.");
        addPolicyBlock(sections, "Choosing or capturing media",
                "When you choose media, Android's system document picker gives Keepsake access to the item you select; the app copies it into its private storage. When you capture a photo or video, Keepsake opens a separate camera app and temporarily prepares a media item before saving it in the journal. The camera app and Android system components are provided by others and follow their own privacy practices.");
        addPolicyBlock(sections, "Retention and deletion",
                "Your journal remains on this device until you delete a memory or uninstall the app. Deleting a memory also deletes its Keepsake copy, caption, and comments. Deleting a Keepsake copy of an imported item does not delete the original you selected. This build does not provide cloud sync or a recovery service.");
        addPolicyBlock(sections, "Security and device changes",
                "The app keeps journal files in app-private storage and Android app backup is disabled. Your device, operating system, camera, or file provider may have separate access or backup behavior. Use your device's screen lock and consider that locally stored memories may be lost if the app or device is removed, reset, damaged, or lost.");
        addPolicyBlock(sections, "Children and personal information",
                "Keepsake has no account or online profile and does not knowingly collect personal information from children. A parent or guardian should supervise a child's use and ensure it is appropriate for them.");

        sections.addView(label("TERMS OF USE", 9, "#8D625C", true), marginTop(20));
        addPolicyBlock(sections, "Your content",
                "You keep ownership of the photos, videos, and text you add. You are responsible for having the rights and permissions needed to use that content and for keeping your device secure.");
        addPolicyBlock(sections, "Personal use and availability",
                "Use Keepsake lawfully and only with media you are allowed to access. The app is a local journal, not a backup service: availability depends on your device, its storage, and compatible Android software. Keep separate copies of anything you cannot afford to lose.");
        addPolicyBlock(sections, "Changes and support",
                "These terms and this notice may be updated with future app versions. For support, use the publisher contact shown on the official distribution listing.");

        sections.addView(label("BEFORE PUBLICATION", 9, "#8D625C", true), marginTop(20));
        sections.addView(label(
                "Store requirements vary and can change. Before distributing Keepsake, the publisher must provide a public privacy-policy URL and support contact on the store listing, and complete the store's current data-safety declarations accurately. This in-app notice is not legal advice or a guarantee of store approval.",
                12, "#625751", false), marginTop(4));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(color("#FBF8F3"));
        scroll.setPadding(dp(4), 0, dp(4), 0);
        scroll.addView(sections);
        int screenHeightDp = Math.round(getResources().getDisplayMetrics().heightPixels
                / getResources().getDisplayMetrics().density);
        int dialogHeight = dp(Math.min(460, Math.round(screenHeightDp * .58f)));
        scroll.setLayoutParams(new ViewGroup.LayoutParams(-1, dialogHeight));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Privacy & app information")
                .setView(scroll)
                .setPositiveButton("Done", null)
                .create();
        dialog.show();
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private void addPolicyBlock(LinearLayout content, String title, String body) {
        TextView heading = label(title, 12, "#354A3C", true);
        LinearLayout.LayoutParams headingParams = marginTop(13);
        content.addView(heading, headingParams);
        TextView text = label(body, 12, "#625751", false);
        text.setLineSpacing(dp(2), 1f);
        content.addView(text, marginTop(3));
    }

    private void renderHome() {
        selectedTab = 0;
        searchField = null;
        screenContent.removeAllViews();
        screenContent.addView(buildHeader(true));
        screenContent.addView(buildWelcomeCard(), marginTop(12));
        screenContent.addView(buildProfileCard(), marginTop(17));
        screenContent.addView(buildProfileStats(), marginTop(7));
        screenContent.addView(sectionHeading("Only yours. Always.", null), marginTop(16));
        screenContent.addView(buildPrivacyCard(), marginTop(6));
        screenContent.addView(buildViewMemoriesButton(), marginTop(10));
        updateNavigation();
        scrollToTop();
    }

    private View buildWelcomeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(0, dp(3), 0, dp(3));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView title = label("Keep what feels like you.", 19, "#2E3B32", true);
        title.setLetterSpacing(-0.035f);
        words.addView(title);
        words.addView(label("A little space for all the good things.", 11, "#77736B", false), marginTop(2));
        card.addView(words, new LinearLayout.LayoutParams(0, -2, 1));

        return card;
    }

    private View sectionHeading(String title, String eyebrow) {
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        if (eyebrow != null && !eyebrow.isEmpty()) {
            copy.addView(label(eyebrow, 9, "#8D796F", true));
        }
        copy.addView(label(title, 18, "#2E3B32", true), marginTop(4));
        heading.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        return heading;
    }

    private LinearLayout buildBottomNavigation() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(color(COLOR_BLUSH));

        View divider = new View(this);
        divider.setBackgroundColor(color("#EAC6BC"));
        bar.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        LinearLayout items = new LinearLayout(this);
        items.setGravity(Gravity.CENTER_VERTICAL);
        items.setPadding(dp(5), dp(5), dp(5), dp(5));
        items.addView(navigationItem("home", "Home", 0), navigationWeight());
        items.addView(navigationItem("create", "Create", 1), navigationWeight());
        items.addView(navigationItem("journal", "Memories", 2), navigationWeight());
        bar.addView(items, new LinearLayout.LayoutParams(-1, dp(63)));
        return bar;
    }

    private LinearLayout.LayoutParams navigationWeight() {
        return new LinearLayout.LayoutParams(0, -1, 1);
    }

    private View navigationItem(String glyph, String title, int tab) {
        LinearLayout item = new LinearLayout(this);
        item.setGravity(Gravity.CENTER);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setBackground(background(COLOR_BLUSH, 18));
        item.setPadding(0, dp(3), 0, dp(2));
        item.setFocusable(true);
        item.setClickable(true);
        item.setContentDescription("create".equals(glyph) ? "Create a memory" : title);

        NavigationIcon icon = new NavigationIcon(this, glyph);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(23), dp(23));
        if ("create".equals(glyph)) {
            icon.setBackground(background("#354A3C", 18));
            iconParams = new LinearLayout.LayoutParams(dp(43), dp(43));
        }
        item.addView(icon, iconParams);

        if (!"create".equals(glyph)) {
            TextView caption = label(title, 10, "#354A3C", false);
            LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-2, -2);
            captionParams.topMargin = dp(3);
            item.addView(caption, captionParams);
        }

        item.setTag(tab);
        item.setOnClickListener(view -> selectTab(tab));
        return item;
    }

    private void updateNavigation() {
        for (int i = 0; i < navigation.getChildCount(); i++) {
            View child = navigation.getChildAt(i);
            if (!(child instanceof LinearLayout)) {
                continue;
            }
            LinearLayout items = (LinearLayout) child;
            for (int j = 0; j < items.getChildCount(); j++) {
                LinearLayout item = (LinearLayout) items.getChildAt(j);
                int tab = (Integer) item.getTag();
                boolean selected = tab == selectedTab && tab != 1;
                item.setBackground(background(selected ? "#FBF1ED" : COLOR_BLUSH, 18));
                NavigationIcon icon = (NavigationIcon) item.getChildAt(0);
                int tint = color(tab == 1 ? COLOR_BLUSH : "#354A3C");
                icon.setColor(tint);
                if (item.getChildCount() > 1) {
                    TextView caption = (TextView) item.getChildAt(1);
                    caption.setTextColor(color("#354A3C"));
                    caption.setTypeface(Typeface.create(selected ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
                }
            }
        }
    }

    private void selectTab(int tab) {
        if (tab == 1) {
            showCreateMenu();
            return;
        }
        if (tab == 0) {
            renderHome();
        } else {
            renderMemories();
        }
    }

    private void renderMemories() {
        selectedTab = 2;
        searchField = null;
        screenContent.removeAllViews();
        screenContent.addView(buildHeader(false));
        screenContent.addView(buildMemoriesProfile(), marginTop(16));
        screenContent.addView(buildMemoriesActions(), marginTop(13));
        screenContent.addView(label("YOUR ALBUMS", 9, "#8D625C", true), marginTop(18));
        screenContent.addView(buildAlbumHighlights(), marginTop(8));

        searchField = buildMemoriesSearch();
        screenContent.addView(searchField, marginTop(18));
        LinearLayout.LayoutParams searchParams = (LinearLayout.LayoutParams) searchField.getLayoutParams();
        searchParams.height = dp(46);
        searchField.setLayoutParams(searchParams);

        LinearLayout filters = new LinearLayout(this);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        TextView dateButton = buildDateFilterButton();
        filters.addView(dateButton, new LinearLayout.LayoutParams(0, dp(39), 1));
        Spinner sortFilter = buildSortFilter();
        LinearLayout.LayoutParams sortParams = new LinearLayout.LayoutParams(0, dp(39), 1);
        sortParams.setMargins(dp(8), 0, 0, 0);
        filters.addView(sortFilter, sortParams);
        screenContent.addView(filters, marginTop(9));

        screenContent.addView(buildMemoriesGridHeading(), marginTop(17));
        feed = new LinearLayout(this);
        feed.setOrientation(LinearLayout.VERTICAL);
        screenContent.addView(feed, marginTop(9));
        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                renderFeed();
            }

            @Override
            public void afterTextChanged(Editable text) {}
        });
        updateNavigation();
        renderFeed();
        scrollToTop();
    }

    private View buildMemoriesProfile() {
        List<PostStore.Post> posts = store.getPosts();
        int commentCount = 0;
        for (PostStore.Post post : posts) {
            commentCount += store.getCommentCount(post.id);
        }

        LinearLayout profile = new LinearLayout(this);
        profile.setGravity(Gravity.CENTER_VERTICAL);
        profile.setPadding(0, dp(8), 0, dp(8));

        LinearLayout stats = new LinearLayout(this);
        stats.setGravity(Gravity.CENTER_VERTICAL);
        stats.addView(profileStat(Integer.toString(posts.size()), "memories"), statWeight());
        stats.addView(profileStat(Integer.toString(store.getAlbums().size()), "albums"), statWeight());
        stats.addView(profileStat(Integer.toString(commentCount), "comments"), statWeight());
        profile.addView(stats, new LinearLayout.LayoutParams(-1, dp(70)));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(label("YOUR PRIVATE COLLECTION", 9, "#8D625C", true));
        content.addView(label("Every moment, kept close.", 20, "#2E3B32", true), marginTop(4));
        TextView description = label(
                "A little photo journal, just for you. Every memory stays on this device.",
                12, "#625751", false);
        description.setLineSpacing(dp(2), 1f);
        content.addView(description, marginTop(4));

        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        result.addView(profile);
        result.addView(content, marginTop(5));
        return result;
    }

    private View profileStat(String count, String caption) {
        LinearLayout stat = new LinearLayout(this);
        stat.setGravity(Gravity.CENTER);
        stat.setOrientation(LinearLayout.VERTICAL);
        stat.addView(label(count, 20, "#2E3B32", true));
        stat.addView(label(caption, 10, "#625751", false), marginTop(2));
        return stat;
    }

    private View buildMemoriesActions() {
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.addView(memoryActionButton("Create album", view -> showCreateAlbumDialog(-1)),
                actionButtonWeight());
        LinearLayout.LayoutParams addParams = actionButtonWeight();
        addParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(memoryActionButton("Add memory", view -> showCreateMenu()), addParams);
        return actions;
    }

    private LinearLayout.LayoutParams actionButtonWeight() {
        return new LinearLayout.LayoutParams(0, dp(42), 1);
    }

    private TextView memoryActionButton(String title, View.OnClickListener listener) {
        TextView button = label(title, 11, "#354A3C", true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(background("#FBF1ED", 13));
        button.setOnClickListener(listener);
        return button;
    }

    private View buildAlbumHighlights() {
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        horizontal.setOverScrollMode(View.OVER_SCROLL_NEVER);
        albumHighlightRow = new LinearLayout(this);
        albumHighlightRow.setGravity(Gravity.TOP);
        for (PostStore.Album album : store.getAlbums()) {
            albumHighlightRow.addView(albumHighlight(album.name, album.id, album.name), albumHighlightMargins());
        }
        TextView add = label("+", 24, "#B66E65", false);
        add.setGravity(Gravity.CENTER);
        add.setBackground(background("#FBF1ED", 50));
        add.setContentDescription("Create album");
        LinearLayout addColumn = albumHighlightColumn("New", add);
        addColumn.setOnClickListener(view -> showCreateAlbumDialog(-1));
        albumHighlightRow.addView(addColumn, albumHighlightMargins());
        horizontal.addView(albumHighlightRow);
        return horizontal;
    }

    private LinearLayout.LayoutParams albumHighlightMargins() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMargins(0, 0, dp(13), 0);
        return params;
    }

    private View albumHighlight(String title, long albumId, String monogram) {
        LinearLayout column = new LinearLayout(this);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setOrientation(LinearLayout.VERTICAL);
        TextView cover = label(albumMonogram(monogram), 14, "#52634C", true);
        cover.setGravity(Gravity.CENTER);
        cover.setTag(Long.valueOf(albumId));
        cover.setBackground(background(albumId == albumFilterId
                ? "#F3E8DF" : "#EFF2E9", 50));
        column.addView(cover, new LinearLayout.LayoutParams(dp(57), dp(57)));
        column.addView(label(title, 9, "#4E4741", false), marginTop(5));
        column.setOnClickListener(view -> {
            albumFilterId = albumFilterId == albumId ? -1 : albumId;
            syncMemoryAlbumFilter();
            updateAlbumHighlightSelection();
            renderFeed();
        });
        return column;
    }

    private void updateAlbumHighlightSelection() {
        if (albumHighlightRow == null) {
            return;
        }
        for (int i = 0; i < albumHighlightRow.getChildCount(); i++) {
            View child = albumHighlightRow.getChildAt(i);
            if (!(child instanceof LinearLayout) || ((LinearLayout) child).getChildCount() == 0) {
                continue;
            }
            View cover = ((LinearLayout) child).getChildAt(0);
            Object tag = cover.getTag();
            if (tag instanceof Long) {
                long id = (Long) tag;
                cover.setBackground(background(id == albumFilterId
                        ? "#F3E8DF" : "#EFF2E9", 50));
            }
        }
    }

    private void syncMemoryAlbumFilter() {
        if (memoryAlbumFilter == null) {
            return;
        }
        int position = 0;
        if (albumFilterId == -2) {
            position = 1;
        } else if (albumFilterId >= 0) {
            List<PostStore.Album> albums = store.getAlbums();
            for (int i = 0; i < albums.size(); i++) {
                if (albums.get(i).id == albumFilterId) {
                    position = i + 2;
                    break;
                }
            }
        }
        memoryAlbumFilter.setSelection(position);
    }

    private LinearLayout albumHighlightColumn(String title, TextView cover) {
        LinearLayout column = new LinearLayout(this);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(cover, new LinearLayout.LayoutParams(dp(57), dp(57)));
        column.addView(label(title, 9, "#4E4741", false), marginTop(5));
        return column;
    }

    private String albumMonogram(String name) {
        String trimmed = name.trim();
        return trimmed.length() <= 2
                ? trimmed.toUpperCase(Locale.ENGLISH)
                : trimmed.substring(0, 1).toUpperCase(Locale.ENGLISH);
    }

    private EditText buildMemoriesSearch() {
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(13);
        search.setTextColor(color("#2E3B32"));
        search.setHintTextColor(color("#8D796F"));
        search.setHint("Search words, dates, albums");
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setBackground(background("#FBF8F3", 14));
        return search;
    }

    private View buildMemoriesGridHeading() {
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(label("YOUR MEMORIES", 10, "#8D625C", true),
                new LinearLayout.LayoutParams(0, -2, 1));
        List<PostStore.Album> albums = store.getAlbums();
        List<String> options = new ArrayList<>();
        options.add("All memories");
        options.add("Unsorted");
        for (PostStore.Album album : albums) {
            options.add(album.name);
        }
        memoryAlbumFilter = styledSpinner(options, "#FBF1ED");
        int selectedPosition = 0;
        if (albumFilterId == -2) {
            selectedPosition = 1;
        } else if (albumFilterId >= 0) {
            for (int i = 0; i < albums.size(); i++) {
                if (albums.get(i).id == albumFilterId) {
                    selectedPosition = i + 2;
                    break;
                }
            }
        }
        memoryAlbumFilter.setSelection(selectedPosition);
        memoryAlbumFilter.setContentDescription("Filter memories by album");
        memoryAlbumFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    albumFilterId = -1;
                } else if (position == 1) {
                    albumFilterId = -2;
                } else {
                    albumFilterId = albums.get(position - 2).id;
                }
                updateAlbumHighlightSelection();
                renderFeed();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(dp(145), dp(39));
        heading.addView(memoryAlbumFilter, filterParams);
        return heading;
    }

    private void scrollToTop() {
        if (scrollContainer != null) {
            scrollContainer.post(() -> scrollContainer.scrollTo(0, 0));
        }
    }

    private View buildProfileCard() {
        LinearLayout profileCard = new LinearLayout(this);
        profileCard.setGravity(Gravity.CENTER_VERTICAL);
        profileCard.setPadding(0, dp(6), 0, dp(6));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(label("A life well noticed.", 22, "#2E3B32", true));
        words.addView(label("Your photos. Your words. Your own.", 11, "#77736B", false), marginTop(3));
        profileCard.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        return profileCard;
    }

    private View buildProfileStats() {
        List<PostStore.Post> posts = store.getPosts();
        int commentCount = 0;
        for (PostStore.Post post : posts) {
            commentCount += store.getCommentCount(post.id);
        }

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(statCard(Integer.toString(posts.size()), "MEMORIES"), statWeight());
        stats.addView(statCard(Integer.toString(commentCount), "YOUR WORDS"), statWeight());
        stats.addView(statCard(Integer.toString(countVideos(posts)), "VIDEOS"), statWeight());
        return stats;
    }

    private View buildPrivacyCard() {
        TextView privacy = label("Private by nature. Saved only on this device.", 12, "#77736B", false);
        privacy.setPadding(0, dp(3), 0, dp(3));
        privacy.setLineSpacing(dp(2), 1f);
        return privacy;
    }

    private View buildViewMemoriesButton() {
        TextView viewMemories = label("OPEN MEMORIES", 11, COLOR_BLUSH, true);
        viewMemories.setGravity(Gravity.CENTER);
        viewMemories.setBackground(background("#354A3C", 16));
        viewMemories.setPadding(dp(18), 0, dp(18), 0);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, dp(49));
        viewMemories.setLayoutParams(buttonParams);
        viewMemories.setOnClickListener(view -> renderMemories());
        return viewMemories;
    }

    private Spinner buildSortFilter() {
        Spinner spinner = styledSpinner(
                new String[]{"Newest first", "Oldest first", "A to Z", "Z to A"}, "#FBF1ED");
        spinner.setSelection(sortOrder);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                sortOrder = position;
                renderFeed();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        return spinner;
    }

    private Spinner styledSpinner(List<String> options, String backgroundColor) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(adapter);
        spinner.setPadding(dp(7), 0, dp(5), 0);
        spinner.setBackground(background(backgroundColor, 14));
        return spinner;
    }

    private Spinner styledSpinner(String[] options, String backgroundColor) {
        List<String> values = new ArrayList<>();
        Collections.addAll(values, options);
        return styledSpinner(values, backgroundColor);
    }

    private Spinner buildPostAlbumPicker(long selectedAlbumId) {
        List<PostStore.Album> albums = store.getAlbums();
        List<String> options = new ArrayList<>();
        options.add("No album");
        for (PostStore.Album album : albums) {
            options.add(album.name);
        }
        Spinner spinner = styledSpinner(options, "#FBF8F3");
        for (int i = 0; i < albums.size(); i++) {
            if (albums.get(i).id == selectedAlbumId) {
                spinner.setSelection(i + 1);
                break;
            }
        }
        return spinner;
    }

    private long selectedAlbumId(Spinner picker) {
        int position = picker.getSelectedItemPosition();
        return position <= 0 ? -1 : store.getAlbums().get(position - 1).id;
    }

    private void showCreateAlbumDialog(long postId) {
        showCreateAlbumDialog(postId, this::refreshCurrentScreen);
    }

    private void showCreateAlbumDialog(long postId, Runnable onCreated) {
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setTextSize(15);
        name.setHint("Album name");
        name.setTextColor(color("#2E3B32"));
        name.setHintTextColor(color("#8D625C"));
        name.setPadding(dp(16), 0, dp(16), 0);
        name.setBackground(background("#FBF8F3", 14));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Create an album")
                .setView(name)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String albumName = name.getText().toString().trim();
                    if (albumName.isEmpty()) {
                        name.setError("Enter an album name.");
                        return;
                    }
                    try {
                        long albumId = store.addAlbum(albumName);
                        if (postId >= 0) {
                            store.setPostAlbum(postId, albumId);
                        }
                        dialog.dismiss();
                        onCreated.run();
                    } catch (android.database.sqlite.SQLiteConstraintException exception) {
                        name.setError("An album with that name already exists.");
                    } catch (Exception exception) {
                        showError("Could not create the album. Please try again.");
                    }
                }));
        dialog.show();
    }

    private TextView buildDateFilterButton() {
        String title = dateFilter < 0
                ? getString(R.string.date_filter)
                : new SimpleDateFormat("d MMM", Locale.ENGLISH).format(new Date(dateFilter));
        TextView button = label(title, 10, "#354A3C", true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(13), 0, dp(13), 0);
        button.setBackground(background("#FBF1ED", 14));
        button.setOnClickListener(view -> {
            Calendar current = Calendar.getInstance();
            if (dateFilter >= 0) {
                current.setTimeInMillis(dateFilter);
            }
            DatePickerDialog picker = new DatePickerDialog(this,
                    (datePicker, year, month, day) -> {
                        Calendar selected = Calendar.getInstance();
                        selected.set(year, month, day, 0, 0, 0);
                        selected.set(Calendar.MILLISECOND, 0);
                        dateFilter = selected.getTimeInMillis();
                        button.setText(new SimpleDateFormat("d MMM", Locale.ENGLISH)
                                .format(new Date(dateFilter)));
                        renderFeed();
                    }, current.get(Calendar.YEAR), current.get(Calendar.MONTH),
                    current.get(Calendar.DAY_OF_MONTH));
            if (dateFilter >= 0) {
                picker.setButton(DatePickerDialog.BUTTON_NEUTRAL, "Clear", (dialog, which) -> {
                    dateFilter = -1;
                    button.setText(R.string.date_filter);
                    renderFeed();
                });
            }
            picker.show();
            if (dateFilter >= 0) {
                picker.getButton(DatePickerDialog.BUTTON_NEUTRAL).setTextColor(color("#B66E65"));
            }
        });
        return button;
    }

    private View statCard(String count, String caption) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(4), dp(9), dp(4), dp(9));
        card.addView(label(count, 24, "#2E3B32", true));
        card.addView(label(caption, 8, "#77736B", true), marginTop(5));
        return card;
    }

    private LinearLayout.LayoutParams statWeight() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(0, 0, dp(8), 0);
        return params;
    }

    private int countVideos(List<PostStore.Post> posts) {
        int count = 0;
        for (PostStore.Post post : posts) {
            if ("video".equals(post.mediaType)) {
                count++;
            }
        }
        return count;
    }

    private void showCreateMenu() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(4));
        content.addView(buildActions());
        new AlertDialog.Builder(this)
                .setTitle("Make a little memory")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    @android.annotation.SuppressLint("GestureBackNavigation")
    public void onBackPressed() {
        handleBackPressed();
    }

    private void handleBackPressed() {
        if (selectedTab != 0) {
            renderHome();
        } else {
            finish();
        }
    }

    private View buildActions() {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout secondRow = new LinearLayout(this);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        firstRow.addView(actionButton("Take a photo", "CAMERA", view -> capturePhoto()), weightedAction());
        firstRow.addView(actionButton("Record a video", "VIDEO", view -> captureVideo()), weightedAction());
        secondRow.addView(actionButton("Choose a photo", "PHOTOS", view -> importPhoto()), weightedAction());
        secondRow.addView(actionButton("Choose a video", "VIDEOS", view -> importVideo()), weightedAction());
        rows.addView(firstRow);
        rows.addView(secondRow, marginTop(10));
        return rows;
    }

    private View actionButton(String title, String eyebrow, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(11), dp(5), dp(11));
        String icon = "camera";
        if ("VIDEO".equals(eyebrow)) {
            icon = "video";
        } else if ("PHOTOS".equals(eyebrow)) {
            icon = "photo";
        } else if ("VIDEOS".equals(eyebrow)) {
            icon = "video";
        }
        card.setBackground(background("#FBF1ED", 18));
        NavigationIcon mark = new NavigationIcon(this, icon);
        mark.setColor(color("#52634C"));
        mark.setBackground(background("#FFFFFF", 14));
        card.addView(mark, new LinearLayout.LayoutParams(dp(35), dp(35)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(8), 0, dp(2), 0);
        text.addView(label(eyebrow, 8, "#8D796F", true));
        TextView actionTitle = label(title, 11, "#2E3B32", true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-2, -2);
        titleParams.topMargin = dp(4);
        text.addView(actionTitle, titleParams);
        card.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        card.setMinimumHeight(dp(72));
        card.setOnClickListener(listener);
        return card;
    }

    private LinearLayout.LayoutParams weightedAction() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(76), 1);
        params.setMargins(0, 0, dp(7), 0);
        return params;
    }

    private void renderFeed() {
        if (feed == null) {
            return;
        }
        feed.removeAllViews();
        List<PostStore.Post> allPosts = store.getPosts();
        List<PostStore.Post> posts = getVisiblePosts(allPosts);
        if (posts.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(24), dp(20), dp(24));
            empty.setBackground(background("#FBF8F3", 20));
            String title = allPosts.isEmpty() ? "Your story starts here." : "No memories found.";
            String detail = allPosts.isEmpty()
                    ? "Use the plus button to capture or choose a photo or video."
                    : "Try another search or clear a filter to see more.";
            empty.addView(label(title, 16, "#2E3B32", true), marginTop(9));
            TextView detailText = label(detail, 12, "#77736B", false);
            detailText.setGravity(Gravity.CENTER);
            empty.addView(detailText, marginTop(5));
            feed.addView(empty);
            return;
        }

        int screenWidthDp = Math.round(getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        int tileSizeDp = (screenWidthDp - 46) / 3;
        LinearLayout row = null;
        for (int i = 0; i < posts.size(); i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
                if (i > 0) {
                    rowParams.topMargin = dp(3);
                }
                feed.addView(row, rowParams);
            }
            FrameLayout tile = buildPost(posts.get(i));
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(0, dp(tileSizeDp), 1);
            tileParams.setMargins(0, 0, i % 3 == 2 ? 0 : dp(3), 0);
            row.addView(tile, tileParams);
        }
        if (row != null && posts.size() % 3 != 0) {
            int remaining = 3 - posts.size() % 3;
            for (int i = 0; i < remaining; i++) {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(tileSizeDp), 1));
            }
        }
    }

    private List<PostStore.Post> getVisiblePosts(List<PostStore.Post> allPosts) {
        List<PostStore.Post> posts = new ArrayList<>();
        String query = searchField == null
                ? "" : searchField.getText().toString().trim().toLowerCase(Locale.ENGLISH);
        for (PostStore.Post post : allPosts) {
            if (albumFilterId >= 0 && post.albumId != albumFilterId
                    || albumFilterId == -2 && post.albumId >= 0
                    || dateFilter >= 0 && !isSameDay(post.createdAt, dateFilter)
                    || !query.isEmpty() && !matchesQuery(post, query)) {
                continue;
            }
            posts.add(post);
        }
        Comparator<PostStore.Post> comparator;
        if (sortOrder == 1) {
            comparator = (first, second) -> Long.compare(first.createdAt, second.createdAt);
        } else if (sortOrder == 2 || sortOrder == 3) {
            comparator = (first, second) -> postSortName(first).compareToIgnoreCase(postSortName(second));
            if (sortOrder == 3) {
                comparator = comparator.reversed();
            }
        } else {
            comparator = (first, second) -> Long.compare(second.createdAt, first.createdAt);
        }
        Collections.sort(posts, comparator);
        return posts;
    }

    private String postSortName(PostStore.Post post) {
        String title = post.caption.isEmpty() ? post.mediaType : post.caption;
        return title.toLowerCase(Locale.ENGLISH);
    }

    private boolean isSameDay(long first, long second) {
        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH);
        return dayFormat.format(new Date(first)).equals(dayFormat.format(new Date(second)));
    }

    private boolean matchesQuery(PostStore.Post post, String query) {
        String date = formatDate(post.createdAt).toLowerCase(Locale.ENGLISH);
        String isoDate = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                .format(new Date(post.createdAt));
        if (post.caption.toLowerCase(Locale.ENGLISH).contains(query)
                || date.contains(query)
                || isoDate.contains(query)
                || post.mediaType.toLowerCase(Locale.ENGLISH).contains(query)
                || post.albumName != null && post.albumName.toLowerCase(Locale.ENGLISH).contains(query)) {
            return true;
        }
        for (PostStore.Comment comment : store.getComments(post.id)) {
            if (comment.text.toLowerCase(Locale.ENGLISH).contains(query)) {
                return true;
            }
        }
        return false;
    }

    private FrameLayout buildPost(PostStore.Post post) {
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(background("#EFE8DF", 3));
        tile.setClipToOutline(true);
        tile.setContentDescription(post.caption.isEmpty()
                ? formatDate(post.createdAt) : post.caption + ", " + formatDate(post.createdAt));

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImageBitmap(readPreview(new File(getFilesDir(), "media/" + post.fileName), post.mediaType));
        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(-1, -1);
        tile.addView(image, imageParams);
        if ("video".equals(post.mediaType)) {
            image.setOnClickListener(view -> playVideo(post));
        } else {
            image.setOnClickListener(view -> showPhotoViewer(post));
            image.setContentDescription("Open photo");
        }

        TextView options = label("MORE", 9, "#B66E65", true);
        options.setGravity(Gravity.CENTER);
        options.setPadding(dp(7), dp(5), dp(7), dp(5));
        options.setBackground(background("#E6FBF8F3", 9));
        options.setContentDescription("Edit or delete this memory");
        options.setOnClickListener(view -> showPostActions(post));
        FrameLayout.LayoutParams optionParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        optionParams.setMargins(0, dp(5), dp(5), 0);
        tile.addView(options, optionParams);

        if ("video".equals(post.mediaType)) {
            TextView play = label("PLAY", 9, "#FBF8F3", true);
            play.setGravity(Gravity.CENTER);
            play.setPadding(dp(8), dp(5), dp(8), dp(5));
            play.setBackground(background("#A6354A3C", 12));
            FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
            play.setOnClickListener(view -> playVideo(post));
            tile.addView(play, playParams);
        }
        int commentCount = store.getCommentCount(post.id);
        TextView comments = label(commentCount == 0 ? "+" : Integer.toString(commentCount),
                9, "#FBF8F3", true);
        comments.setGravity(Gravity.CENTER);
        comments.setPadding(dp(7), dp(4), dp(7), dp(4));
        comments.setBackground(background("#A6354A3C", 9));
        comments.setContentDescription("Add or view comments");
        comments.setOnClickListener(view -> showComments(post));
        FrameLayout.LayoutParams commentParams = new FrameLayout.LayoutParams(-2, -2,
                Gravity.BOTTOM | Gravity.START);
        commentParams.setMargins(dp(5), 0, 0, dp(5));
        tile.addView(comments, commentParams);
        return tile;
    }

    private void importPhoto() {
        launchImport(REQUEST_IMPORT_PHOTO, "image/*");
    }

    private void importVideo() {
        launchImport(REQUEST_IMPORT_VIDEO, "video/*");
    }

    private void launchImport(int requestCode, String mimeType) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, requestCode);
        } catch (Exception exception) {
            showError("Could not open your media library.");
        }
    }

    private void capturePhoto() {
        launchCapture(REQUEST_CAPTURE_PHOTO, "image");
    }

    private void captureVideo() {
        launchCapture(REQUEST_CAPTURE_VIDEO, "video");
    }

    private void launchCapture(int requestCode, String mediaType) {
        String mimeType = "image".equals(mediaType) ? "image/jpeg" : "video/mp4";
        Uri destination = createCaptureDestination(mediaType, mimeType);
        if (destination == null) {
            showError("Could not prepare the camera.");
            return;
        }
        Intent intent = new Intent("image".equals(mediaType)
                ? MediaStore.ACTION_IMAGE_CAPTURE : MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, destination);
        intent.setClipData(android.content.ClipData.newRawUri("Keepsake media", destination));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (intent.resolveActivity(getPackageManager()) == null) {
            getContentResolver().delete(destination, null, null);
            showError("No camera app is available on this device.");
            return;
        }
        pendingUri = destination;
        pendingMediaType = mediaType;
        try {
            startActivityForResult(intent, requestCode);
        } catch (Exception exception) {
            getContentResolver().delete(destination, null, null);
            pendingUri = null;
            pendingMediaType = null;
            showError("Could not start the camera.");
        }
    }

    private Uri createCaptureDestination(String mediaType, String mimeType) {
        ContentValues values = new ContentValues();
        String timestamp = Long.toString(System.currentTimeMillis());
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,
                "keepsake_" + timestamp + ("image".equals(mediaType) ? ".jpg" : ".mp4"));
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        if ("image".equals(mediaType)) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Keepsake");
            return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        }
        values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Keepsake");
        return getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            removeCaptureDestination();
            return;
        }

        Uri source = pendingUri;
        String mediaType = pendingMediaType;
        if (requestCode == REQUEST_IMPORT_PHOTO || requestCode == REQUEST_IMPORT_VIDEO) {
            source = data == null ? null : data.getData();
            mediaType = requestCode == REQUEST_IMPORT_PHOTO ? "image" : "video";
        }
        Uri capturedUri = (requestCode == REQUEST_CAPTURE_PHOTO || requestCode == REQUEST_CAPTURE_VIDEO)
                ? pendingUri : null;
        pendingUri = null;
        pendingMediaType = null;
        if (source == null || mediaType == null) {
            showError("Could not read the selected media.");
            return;
        }
        importIntoPrivateStorage(source, mediaType, capturedUri);
    }

    private void importIntoPrivateStorage(Uri source, String mediaType, Uri capturedUri) {
        ioExecutor.execute(() -> {
            File copiedFile = null;
            try {
                File directory = new File(getFilesDir(), "media");
                if (!directory.exists() && !directory.mkdirs()) {
                    throw new java.io.IOException("Could not create private media directory");
                }
                String mimeType = getContentResolver().getType(source);
                String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                if (extension == null || extension.isEmpty()) {
                    extension = "image".equals(mediaType) ? "jpg" : "mp4";
                }
                extension = "." + extension;
                copiedFile = File.createTempFile("post_", extension, directory);
                try (InputStream input = getContentResolver().openInputStream(source);
                     FileOutputStream output = new FileOutputStream(copiedFile)) {
                    if (input == null) {
                        throw new java.io.IOException("Media stream is unavailable");
                    }
                    byte[] buffer = new byte[32 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                    output.getFD().sync();
                }
                File savedFile = copiedFile;
                runOnUiThread(() -> showPostEditor(savedFile, mediaType, capturedUri));
            } catch (Exception exception) {
                if (copiedFile != null) {
                    copiedFile.delete();
                }
                runOnUiThread(() -> {
                    removeCaptureUri(capturedUri);
                    showError("Could not save your media. Please try again.");
                });
            }
        });
    }

    private void showPostEditor(File mediaFile, String mediaType, Uri capturedUri) {
        EditText caption = new EditText(this);
        caption.setHint("Write a caption");
        caption.setTextSize(15);
        caption.setMinLines(2);
        caption.setGravity(Gravity.TOP | Gravity.START);
        int inset = dp(20);
        caption.setPadding(inset, dp(10), inset, dp(10));
        Spinner albumPicker = buildPostAlbumPicker(-1);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(4));
        content.addView(label("CAPTION", 9, "#8D625C", true));
        content.addView(caption, marginTop(5));
        content.addView(label("ALBUM", 9, "#8D625C", true), marginTop(12));
        content.addView(albumPicker, marginTop(5));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("New post")
                .setView(content)
                .setNegativeButton("Cancel", (alert, which) -> {
                    mediaFile.delete();
                    removeCaptureUri(capturedUri);
                })
                .setPositiveButton("Save post", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                store.addPost(mediaFile.getName(), mediaType, caption.getText().toString().trim(),
                        selectedAlbumId(albumPicker));
                removeCaptureUri(capturedUri);
                dialog.dismiss();
                renderHome();
                Toast.makeText(this, "Post saved on your device.", Toast.LENGTH_SHORT).show();
            } catch (Exception exception) {
                showError("Could not save your post. Please try again.");
            }
        }));
        dialog.setOnCancelListener(ignored -> {
            mediaFile.delete();
            removeCaptureUri(capturedUri);
        });
        dialog.show();
    }

    private void showPostActions(PostStore.Post post) {
        showPostActions(post, this::refreshCurrentScreen);
    }

    private void showPostActions(PostStore.Post post, Runnable onUpdated) {
        String[] actions = {"Edit caption", "Remove caption", "Choose album", "Delete memory"};
        new AlertDialog.Builder(this)
                .setTitle("Memory options")
                .setItems(actions, (dialog, selected) -> {
                    if (selected == 0) {
                        showEditCaption(post, onUpdated);
                    } else if (selected == 1) {
                        showRemoveCaptionConfirmation(post, onUpdated);
                    } else if (selected == 2) {
                        showChooseAlbum(post, onUpdated);
                    } else {
                        showDeleteMemoryConfirmation(post, onUpdated);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditCaption(PostStore.Post post, Runnable onUpdated) {
        EditText caption = new EditText(this);
        caption.setText(post.caption);
        caption.setHint("Write a caption");
        caption.setTextSize(15);
        caption.setMinLines(2);
        caption.setGravity(Gravity.TOP | Gravity.START);
        caption.setPadding(dp(20), dp(10), dp(20), dp(10));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit caption")
                .setView(caption)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    try {
                        store.updateCaption(post.id, caption.getText().toString().trim());
                        dialog.dismiss();
                        onUpdated.run();
                    } catch (Exception exception) {
                        showError("Could not update the caption. Please try again.");
                    }
                }));
        dialog.show();
    }

    private void showRemoveCaptionConfirmation(PostStore.Post post, Runnable onUpdated) {
        new AlertDialog.Builder(this)
                .setTitle("Remove caption?")
                .setMessage("This removes the text from this memory. The photo or video will stay saved.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    try {
                        store.updateCaption(post.id, "");
                        onUpdated.run();
                    } catch (Exception exception) {
                        showError("Could not remove the caption.");
                    }
                })
                .show();
    }

    private void showChooseAlbum(PostStore.Post post, Runnable onUpdated) {
        List<PostStore.Album> albums = store.getAlbums();
        String[] options = new String[albums.size() + 2];
        options[0] = "No album";
        for (int i = 0; i < albums.size(); i++) {
            options[i + 1] = albums.get(i).name;
        }
        options[options.length - 1] = "Create a new album";
        new AlertDialog.Builder(this)
                .setTitle("Add to an album")
                .setItems(options, (dialog, selected) -> {
                    if (selected == options.length - 1) {
                        showCreateAlbumDialog(post.id, onUpdated);
                        return;
                    }
                    try {
                        store.setPostAlbum(post.id, selected == 0 ? -1 : albums.get(selected - 1).id);
                        onUpdated.run();
                    } catch (Exception exception) {
                        showError("Could not update the album. Please try again.");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteMemoryConfirmation(PostStore.Post post, Runnable onUpdated) {
        new AlertDialog.Builder(this)
                .setTitle("Delete this memory?")
                .setMessage("The saved photo or video, caption, and comments will be removed from this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    File media = new File(getFilesDir(), "media/" + post.fileName);
                    if (media.exists() && !media.delete()) {
                        showError("Could not delete the saved media.");
                        return;
                    }
                    try {
                        store.deletePost(post.id);
                        onUpdated.run();
                    } catch (Exception exception) {
                        showError("Could not delete this memory. Please try again.");
                    }
                })
                .show();
    }

    private void refreshCurrentScreen() {
        if (selectedTab == 2) {
            renderMemories();
        } else {
            renderHome();
        }
    }

    private void showPhotoViewer(PostStore.Post post) {
        List<PostStore.Post> photos = new ArrayList<>();
        int selectedIndex = 0;
        for (PostStore.Post candidate : getVisiblePosts(store.getPosts())) {
            if (!"image".equals(candidate.mediaType)) {
                continue;
            }
            if (candidate.id == post.id) {
                selectedIndex = photos.size();
            }
            photos.add(candidate);
        }
        if (photos.isEmpty()) {
            photos.add(post);
        }
        PhotoViewerState state = new PhotoViewerState(photos, selectedIndex);
        Dialog dialog = new Dialog(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
        LinearLayout viewer = new LinearLayout(this);
        viewer.setOrientation(LinearLayout.VERTICAL);
        viewer.setBackgroundColor(color("#FBF8F3"));
        viewer.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset;
            int bottomInset;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets systemBars = insets.getInsets(WindowInsets.Type.systemBars());
                android.graphics.Insets imeInsets = insets.getInsets(WindowInsets.Type.ime());
                topInset = systemBars.top;
                bottomInset = Math.max(systemBars.bottom, imeInsets.bottom);
            } else {
                topInset = insets.getSystemWindowInsetTop();
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            viewer.setPadding(0, topInset, 0, bottomInset);
            return insets;
        });

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(16), 0, dp(16), 0);
        TextView close = label("CLOSE", 10, "#354A3C", true);
        close.setOnClickListener(view -> dialog.dismiss());
        toolbar.addView(close, new LinearLayout.LayoutParams(dp(54), dp(48)));
        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.VERTICAL);
        title.addView(label("Keepsake", 14, "#2E3B32", true));
        state.dateLabel = label(formatDate(post.createdAt), 10, "#77736B", false);
        title.addView(state.dateLabel, marginTop(2));
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView more = label("MORE", 10, "#52634C", true);
        more.setGravity(Gravity.CENTER);
        more.setPadding(dp(8), dp(10), 0, dp(10));
        more.setOnClickListener(view -> showPostActions(state.currentPost(), () -> {
            dialog.dismiss();
            refreshCurrentScreen();
        }));
        toolbar.addView(more);
        viewer.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(54)));

        PhotoPreviewImageView image = new PhotoPreviewImageView(this, state,
                () -> showExpandedPhoto(state));
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription("Photo. Tap to zoom, or swipe to browse photos.");
        image.setBackgroundColor(color("#2E3B32"));
        int imageHeight = Math.min(dp(420), Math.round(getResources().getDisplayMetrics().heightPixels * .43f));
        viewer.addView(image, new LinearLayout.LayoutParams(-1, imageHeight));
        state.previewImage = image;

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(18), dp(14), dp(18), dp(18));
        state.details = details;
        ScrollView detailsScroll = new ScrollView(this);
        detailsScroll.setFillViewport(false);
        detailsScroll.addView(details);
        viewer.addView(detailsScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        state.detailsScroll = detailsScroll;

        EditText commentInput = new EditText(this);
        commentInput.setSingleLine(true);
        commentInput.setTextSize(14);
        commentInput.setHint("Add a comment...");
        commentInput.setTextColor(color("#2E3B32"));
        commentInput.setHintTextColor(color("#8D796F"));
        commentInput.setPadding(dp(12), 0, dp(12), 0);
        commentInput.setBackground(background("#F3E9E3", 22));
        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(12), dp(9), dp(12), dp(9));
        composer.addView(commentInput, new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView postComment = label("POST", 10, "#52634C", true);
        postComment.setGravity(Gravity.CENTER);
        postComment.setPadding(dp(13), 0, dp(5), 0);
        composer.addView(postComment, new LinearLayout.LayoutParams(-2, dp(44)));
        postComment.setOnClickListener(view -> {
            String text = commentInput.getText().toString().trim();
            if (text.isEmpty()) {
                commentInput.setError("Write a comment first.");
                return;
            }
            try {
                store.addComment(state.currentPost().id, text);
                commentInput.setText("");
                state.refreshDetails();
                renderFeed();
            } catch (Exception exception) {
                showError("Could not save your comment.");
            }
        });
        viewer.addView(composer, new LinearLayout.LayoutParams(-1, -2));

        state.commentInput = commentInput;
        state.refreshDetails();
        dialog.setContentView(viewer);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(-1, -1);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.getWindow().setStatusBarColor(color("#FBF8F3"));
            dialog.getWindow().setNavigationBarColor(color("#FBF8F3"));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                dialog.getWindow().setDecorFitsSystemWindows(false);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                dialog.getWindow().setNavigationBarContrastEnforced(false);
            }
        }
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(-1, -1);
            viewer.requestApplyInsets();
        }
    }

    private Bitmap loadPhoto(PostStore.Post post) {
        File file = new File(getFilesDir(), "media/" + post.fileName);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (Math.max(bounds.outWidth / (options.inSampleSize * 2),
                bounds.outHeight / (options.inSampleSize * 2)) >= 1600) {
            options.inSampleSize *= 2;
        }
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private void showExpandedPhoto(PhotoViewerState state) {
        if (state.photo == null) {
            showError("Could not open this photo.");
            return;
        }
        Dialog dialog = new Dialog(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(color("#20251F"));

        ZoomablePhotoView image = new ZoomablePhotoView(this,
                delta -> state.movePhoto(delta));
        image.setContentDescription("Zoomable photo. Pinch to zoom and swipe to browse.");
        image.setImageBitmap(state.photo);
        root.addView(image, new FrameLayout.LayoutParams(-1, -1));
        state.expandedImage = image;

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(18), 0, dp(18), 0);
        toolbar.setBackgroundColor(color("#AA20251F"));
        TextView close = label("CLOSE", 10, "#FBF8F3", true);
        close.setOnClickListener(view -> dialog.dismiss());
        toolbar.addView(close, new LinearLayout.LayoutParams(dp(60), dp(52)));
        state.expandedDateLabel = label(formatDate(state.currentPost().createdAt),
                11, "#FBF8F3", true);
        toolbar.addView(state.expandedDateLabel, new LinearLayout.LayoutParams(0, -2, 1));
        state.expandedCountLabel = label(photoPositionText(state), 10, "#F3E9E3", false);
        toolbar.addView(state.expandedCountLabel);
        FrameLayout.LayoutParams toolbarParams = new FrameLayout.LayoutParams(
                -1, dp(56), Gravity.TOP);
        root.addView(toolbar, toolbarParams);

        TextView hint = label("Pinch to zoom  |  Swipe to browse", 10, "#F3E9E3", false);
        hint.setGravity(Gravity.CENTER);
        hint.setBackgroundColor(color("#AA20251F"));
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                -1, dp(42), Gravity.BOTTOM);
        root.addView(hint, hintParams);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset;
            int bottomInset;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets systemBars = insets.getInsets(WindowInsets.Type.systemBars());
                topInset = systemBars.top;
                bottomInset = systemBars.bottom;
            } else {
                topInset = insets.getSystemWindowInsetTop();
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            FrameLayout.LayoutParams updatedToolbarParams =
                    (FrameLayout.LayoutParams) toolbar.getLayoutParams();
            int safeTopInset = Math.max(topInset, dp(24));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    && insets.getDisplayCutout() != null) {
                safeTopInset = Math.max(safeTopInset,
                        insets.getDisplayCutout().getSafeInsetTop());
            }
            updatedToolbarParams.topMargin = safeTopInset;
            toolbar.setLayoutParams(updatedToolbarParams);
            FrameLayout.LayoutParams updatedHintParams =
                    (FrameLayout.LayoutParams) hint.getLayoutParams();
            updatedHintParams.bottomMargin = bottomInset;
            hint.setLayoutParams(updatedHintParams);
            return insets;
        });

        dialog.setContentView(root);
        dialog.setOnDismissListener(ignored -> {
            state.expandedImage = null;
            state.expandedDateLabel = null;
            state.expandedCountLabel = null;
        });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setStatusBarColor(color("#20251F"));
            dialog.getWindow().setNavigationBarColor(color("#20251F"));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                dialog.getWindow().setDecorFitsSystemWindows(false);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                dialog.getWindow().setNavigationBarContrastEnforced(false);
            }
            dialog.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(-1, -1);
        }
    }

    private String photoPositionText(PhotoViewerState state) {
        return (state.index + 1) + " / " + state.photos.size();
    }

    private void populatePhotoDetails(LinearLayout details, PostStore.Post post,
                                     Runnable refreshDetails) {
        details.removeAllViews();
        details.addView(label("YOUR MEMORY", 9, "#8D625C", true));
        if (!post.caption.isEmpty()) {
            LinearLayout caption = new LinearLayout(this);
            caption.setPadding(0, dp(8), 0, dp(12));
            TextView author = label("Keepsake", 13, "#2E3B32", true);
            caption.addView(author);
            TextView text = label("  " + post.caption, 13, "#4E4741", false);
            text.setLineSpacing(dp(2), 1f);
            caption.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
            details.addView(caption);
        } else {
            details.addView(label("No caption", 13, "#77736B", false), marginTop(8));
        }

        View divider = new View(this);
        divider.setBackgroundColor(color("#E9DDD6"));
        details.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(0, dp(13), 0, dp(8));
        heading.addView(label("Comments", 14, "#2E3B32", true),
                new LinearLayout.LayoutParams(0, -2, 1));
        heading.addView(label(Integer.toString(store.getCommentCount(post.id)),
                11, "#77736B", false));
        details.addView(heading);

        List<PostStore.Comment> comments = store.getComments(post.id);
        if (comments.isEmpty()) {
            details.addView(label("Got something on your mind? Add a comment.", 12, "#77736B", false),
                    marginTop(5));
            return;
        }
        for (PostStore.Comment comment : comments) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            TextView author = label("You", 12, "#354A3C", true);
            row.addView(author, new LinearLayout.LayoutParams(dp(36), -2));
            TextView text = label(comment.text, 13, "#4E4741", false);
            text.setLineSpacing(dp(2), 1f);
            row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
            row.setPadding(0, dp(7), 0, dp(7));
            row.setOnClickListener(view -> showCommentActions(post, comment,
                    refreshDetails));
            details.addView(row);
        }
    }

    private void showCommentActions(PostStore.Post post, PostStore.Comment comment, Runnable onUpdated) {
        String[] actions = {"Edit comment", "Delete comment"};
        new AlertDialog.Builder(this)
                .setTitle("Comment options")
                .setItems(actions, (dialog, selected) -> {
                    if (selected == 0) {
                        showEditComment(comment, onUpdated);
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("Delete comment?")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Delete", (confirm, which) -> {
                                    try {
                                        store.deleteComment(comment.id);
                                        onUpdated.run();
                                    } catch (Exception exception) {
                                        showError("Could not delete the comment.");
                                    }
                                })
                                .show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditComment(PostStore.Comment comment, Runnable onUpdated) {
        EditText input = new EditText(this);
        input.setText(comment.text);
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setHint("Write a comment");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit comment")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String updated = input.getText().toString().trim();
                    if (updated.isEmpty()) {
                        input.setError("A comment cannot be empty.");
                        return;
                    }
                    try {
                        store.updateComment(comment.id, updated);
                        dialog.dismiss();
                        onUpdated.run();
                    } catch (Exception exception) {
                        showError("Could not update the comment.");
                    }
                }));
        dialog.show();
    }

    private void showComments(PostStore.Post post) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(8), dp(22), 0);
        List<PostStore.Comment> comments = store.getComments(post.id);
        final AlertDialog[] commentDialog = new AlertDialog[1];
        if (comments.isEmpty()) {
            content.addView(label("No comments yet.", 14, "#74736B", false), marginBottom(12));
        } else {
            LinearLayout commentList = new LinearLayout(this);
            commentList.setOrientation(LinearLayout.VERTICAL);
            for (PostStore.Comment comment : comments) {
                TextView text = label(comment.text, 14, "#2E3B32", false);
                text.setPadding(0, 0, 0, dp(12));
                text.setOnClickListener(view -> showCommentActions(post, comment, () -> {
                    if (commentDialog[0] != null) {
                        commentDialog[0].dismiss();
                    }
                    showComments(post);
                }));
                commentList.addView(text);
            }
            ScrollView commentScroll = new ScrollView(this);
            commentScroll.addView(commentList);
            content.addView(commentScroll, new LinearLayout.LayoutParams(-1, dp(220)));
        }
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setHint("Write a comment");
        content.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Your comments")
                .setView(content)
                .setNegativeButton("Close", null)
                .setPositiveButton("Add comment", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) {
                input.setError("Write a comment first.");
                return;
            }
            try {
                store.addComment(post.id, text);
                dialog.dismiss();
                renderFeed();
                showComments(post);
            } catch (Exception exception) {
                showError("Could not save your comment.");
            }
        }));
        dialog.show();
        commentDialog[0] = dialog;
    }

    private void playVideo(PostStore.Post post) {
        VideoView player = new VideoView(this);
        player.setLayoutParams(new ViewGroup.LayoutParams(-1, dp(320)));
        player.setVideoPath(new File(getFilesDir(), "media/" + post.fileName).getAbsolutePath());
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Your video")
                .setView(player)
                .setNegativeButton("Close", (alert, which) -> player.stopPlayback())
                .create();
        player.setOnPreparedListener(mediaPlayer -> {
            mediaPlayer.setOnVideoSizeChangedListener((videoPlayer, width, height) -> {
                ViewGroup.LayoutParams params = player.getLayoutParams();
                params.height = Math.max(dp(220), Math.min(dp(480), height * dp(320) / Math.max(width, 1)));
                player.setLayoutParams(params);
                player.requestLayout();
            });
            mediaPlayer.start();
        });
        dialog.setOnDismissListener(ignored -> player.stopPlayback());
        dialog.show();
    }

    private Bitmap readPreview(File file, String mediaType) {
        try {
            if ("video".equals(mediaType)) {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                Bitmap frame;
                try {
                    retriever.setDataSource(file.getAbsolutePath());
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        frame = retriever.getScaledFrameAtTime(
                                1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 480);
                    } else {
                        frame = retriever.getFrameAtTime(
                                1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    }
                } finally {
                    retriever.release();
                }
                if (frame != null) {
                    return frame;
                }
            } else {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1;
                while (Math.max(bounds.outWidth / (options.inSampleSize * 2),
                        bounds.outHeight / (options.inSampleSize * 2)) >= 480) {
                    options.inSampleSize *= 2;
                }
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                if (bitmap != null) {
                    return bitmap;
                }
            }
        } catch (Exception exception) {
            return placeholderBitmap();
        }
        return placeholderBitmap();
    }

    private Bitmap placeholderBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color("#E9EADD"));
        return bitmap;
    }

    private void removeCaptureDestination() {
        Uri uri = pendingUri;
        pendingUri = null;
        pendingMediaType = null;
        removeCaptureUri(uri);
    }

    private void removeCaptureUri(Uri uri) {
        if (uri != null) {
            getContentResolver().delete(uri, null, null);
        }
    }

    private String formatDate(long timestamp) {
        return new SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(new Date(timestamp));
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private TextView label(String text, int sizeSp, String hex, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color(hex));
        view.setFontFeatureSettings("kern");
        if (bold) {
            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return view;
    }

    private GradientDrawable background(String hex, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(hex));
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int color(String hex) {
        return Color.parseColor(hex);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams marginTop(int dp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(dp);
        return params;
    }

    private LinearLayout.LayoutParams marginBottom(int dp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(dp);
        return params;
    }

    private final class PhotoViewerState {
        private final List<PostStore.Post> photos;
        private int index;
        private ImageView previewImage;
        private ZoomablePhotoView expandedImage;
        private LinearLayout details;
        private ScrollView detailsScroll;
        private EditText commentInput;
        private TextView dateLabel;
        private TextView expandedDateLabel;
        private TextView expandedCountLabel;
        private Bitmap photo;

        PhotoViewerState(List<PostStore.Post> photos, int index) {
            this.photos = photos;
            this.index = index;
        }

        PostStore.Post currentPost() {
            return photos.get(index);
        }

        void movePhoto(int offset) {
            int nextIndex = index + offset;
            if (nextIndex < 0 || nextIndex >= photos.size()) {
                return;
            }
            Bitmap nextPhoto = loadPhoto(photos.get(nextIndex));
            if (nextPhoto == null) {
                showError("Could not open this photo.");
                return;
            }
            index = nextIndex;
            photo = nextPhoto;
            previewImage.setImageBitmap(photo);
            dateLabel.setText(formatDate(currentPost().createdAt));
            if (commentInput != null) {
                commentInput.setText("");
            }
            refreshDetails();
            if (expandedImage != null) {
                expandedImage.setImageBitmap(photo);
                expandedDateLabel.setText(formatDate(currentPost().createdAt));
                expandedCountLabel.setText(photoPositionText(this));
            }
        }

        void refreshDetails() {
            if (photo == null) {
                photo = loadPhoto(currentPost());
            }
            if (photo == null) {
                showError("Could not open this photo.");
                return;
            }
            if (previewImage != null) {
                previewImage.setImageBitmap(photo);
            }
            if (details != null) {
                populatePhotoDetails(details, currentPost(), this::refreshDetails);
                detailsScroll.scrollTo(0, 0);
            }
        }
    }

    private interface PhotoSwipeListener {
        void onSwipe(int offset);
    }

    private final class PhotoPreviewImageView extends ImageView {
        private final GestureDetector gestureDetector;
        private boolean swiped;

        PhotoPreviewImageView(Context context, PhotoViewerState state, Runnable onTap) {
            super(context);
            setClickable(true);
            setOnClickListener(view -> onTap.run());
            gestureDetector = new GestureDetector(context,
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDown(MotionEvent event) {
                            return true;
                        }

                        @Override
                        public boolean onFling(MotionEvent first, MotionEvent last,
                                               float velocityX, float velocityY) {
                            float deltaX = last.getX() - first.getX();
                            float deltaY = last.getY() - first.getY();
                            if (Math.abs(deltaX) > dp(64)
                                    && Math.abs(deltaX) > Math.abs(deltaY) * 1.2f) {
                                swiped = true;
                                state.movePhoto(deltaX < 0 ? 1 : -1);
                                return true;
                            }
                            return false;
                        }
                    });
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                swiped = false;
            }
            gestureDetector.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP && !swiped) {
                performClick();
            }
            return true;
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }
    }

    private final class ZoomablePhotoView extends ImageView {
        private final Matrix imageMatrix = new Matrix();
        private final RectF displayedImage = new RectF();
        private final ScaleGestureDetector scaleDetector;
        private final GestureDetector gestureDetector;
        private final PhotoSwipeListener swipeListener;
        private float zoomScale = 1f;
        private float lastTouchX;
        private float lastTouchY;
        private boolean swiped;

        ZoomablePhotoView(Context context, PhotoSwipeListener swipeListener) {
            super(context);
            this.swipeListener = swipeListener;
            setScaleType(ScaleType.MATRIX);
            scaleDetector = new ScaleGestureDetector(context,
                    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        @Override
                        public boolean onScale(ScaleGestureDetector detector) {
                            float targetScale = Math.max(1f, Math.min(5f,
                                    zoomScale * detector.getScaleFactor()));
                            float factor = targetScale / zoomScale;
                            imageMatrix.postScale(factor, factor,
                                    detector.getFocusX(), detector.getFocusY());
                            zoomScale = targetScale;
                            constrainImage();
                            setImageMatrix(imageMatrix);
                            return true;
                        }
                    });
            gestureDetector = new GestureDetector(context,
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDown(MotionEvent event) {
                            return true;
                        }

                        @Override
                        public boolean onSingleTapUp(MotionEvent event) {
                            performClick();
                            return true;
                        }

                        @Override
                        public boolean onDoubleTap(MotionEvent event) {
                            toggleZoom(event.getX(), event.getY());
                            return true;
                        }

                        @Override
                        public boolean onFling(MotionEvent first, MotionEvent last,
                                               float velocityX, float velocityY) {
                            float deltaX = last.getX() - first.getX();
                            float deltaY = last.getY() - first.getY();
                            if (zoomScale <= 1f && Math.abs(deltaX) > dp(64)
                                    && Math.abs(deltaX) > Math.abs(deltaY) * 1.2f
                                    && Math.abs(velocityX) > dp(250)) {
                                swiped = true;
                                swipeListener.onSwipe(deltaX < 0 ? 1 : -1);
                                return true;
                            }
                            return false;
                        }
                    });
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            boolean handled = handleTouch(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    && !swiped && !scaleDetector.isInProgress()) {
                performClick();
            }
            return handled;
        }

        @Override
        public void setImageBitmap(Bitmap bitmap) {
            super.setImageBitmap(bitmap);
            zoomScale = 1f;
            post(this::resetImageMatrix);
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            resetImageMatrix();
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }

        private boolean handleTouch(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    swiped = false;
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (zoomScale > 1f && event.getPointerCount() == 1
                            && !scaleDetector.isInProgress()) {
                        float deltaX = event.getX() - lastTouchX;
                        float deltaY = event.getY() - lastTouchY;
                        imageMatrix.postTranslate(deltaX, deltaY);
                        constrainImage();
                        setImageMatrix(imageMatrix);
                    }
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    break;
                default:
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    break;
            }
            return true;
        }

        private void toggleZoom(float x, float y) {
            if (zoomScale > 1f) {
                zoomScale = 1f;
                resetImageMatrix();
                return;
            }
            zoomScale = 2.5f;
            imageMatrix.postScale(zoomScale, zoomScale, x, y);
            constrainImage();
            setImageMatrix(imageMatrix);
        }

        private void resetImageMatrix() {
            if (getDrawable() == null || getWidth() == 0 || getHeight() == 0) {
                return;
            }
            float imageWidth = getDrawable().getIntrinsicWidth();
            float imageHeight = getDrawable().getIntrinsicHeight();
            if (imageWidth <= 0 || imageHeight <= 0) {
                return;
            }
            float fitScale = Math.min(getWidth() / imageWidth, getHeight() / imageHeight);
            float left = (getWidth() - imageWidth * fitScale) / 2f;
            float top = (getHeight() - imageHeight * fitScale) / 2f;
            imageMatrix.setScale(fitScale, fitScale);
            imageMatrix.postTranslate(left, top);
            zoomScale = 1f;
            setImageMatrix(imageMatrix);
        }

        private void constrainImage() {
            if (getDrawable() == null) {
                return;
            }
            displayedImage.set(0, 0, getDrawable().getIntrinsicWidth(),
                    getDrawable().getIntrinsicHeight());
            imageMatrix.mapRect(displayedImage);

            float deltaX;
            if (displayedImage.width() <= getWidth()) {
                deltaX = getWidth() / 2f - displayedImage.centerX();
            } else if (displayedImage.left > 0) {
                deltaX = -displayedImage.left;
            } else if (displayedImage.right < getWidth()) {
                deltaX = getWidth() - displayedImage.right;
            } else {
                deltaX = 0;
            }

            float deltaY;
            if (displayedImage.height() <= getHeight()) {
                deltaY = getHeight() / 2f - displayedImage.centerY();
            } else if (displayedImage.top > 0) {
                deltaY = -displayedImage.top;
            } else if (displayedImage.bottom < getHeight()) {
                deltaY = getHeight() - displayedImage.bottom;
            } else {
                deltaY = 0;
            }
            imageMatrix.postTranslate(deltaX, deltaY);
        }
    }

    private final class NavigationIcon extends View {
        private final String glyph;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path iconPath = new Path();
        private int iconColor = color("#969187");

        NavigationIcon(Context context, String glyph) {
            super(context);
            this.glyph = glyph;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setColor(int value) {
            iconColor = value;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int save = canvas.save();
            float scale = Math.min(getWidth(), getHeight()) / 24f;
            canvas.translate((getWidth() - 24 * scale) / 2f, (getHeight() - 24 * scale) / 2f);
            canvas.scale(scale, scale);
            paint.setColor(iconColor);
            paint.setStrokeWidth(1.8f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(Paint.Style.STROKE);

            if ("home".equals(glyph)) {
                iconPath.reset();
                iconPath.moveTo(3.5f, 10.5f);
                iconPath.lineTo(12, 3.5f);
                iconPath.lineTo(20.5f, 10.5f);
                iconPath.lineTo(20.5f, 20f);
                iconPath.lineTo(14.5f, 20f);
                iconPath.lineTo(14.5f, 14f);
                iconPath.lineTo(9.5f, 14f);
                iconPath.lineTo(9.5f, 20f);
                iconPath.lineTo(3.5f, 20f);
                iconPath.close();
                canvas.drawPath(iconPath, paint);
            } else if ("search".equals(glyph)) {
                canvas.drawCircle(10.5f, 10.5f, 6.3f, paint);
                canvas.drawLine(15.2f, 15.2f, 20.5f, 20.5f, paint);
            } else if ("create".equals(glyph)) {
                paint.setStrokeWidth(1.9f);
                canvas.drawLine(12, 5, 12, 19, paint);
                canvas.drawLine(5, 12, 19, 12, paint);
            } else if ("journal".equals(glyph)) {
                canvas.drawRoundRect(4.5f, 3.5f, 19.5f, 20.5f, 2, 2, paint);
                canvas.drawLine(8, 8, 16, 8, paint);
                canvas.drawLine(8, 12, 16, 12, paint);
                canvas.drawLine(8, 16, 13, 16, paint);
            } else if ("profile".equals(glyph)) {
                canvas.drawCircle(12, 8, 4, paint);
                iconPath.reset();
                iconPath.moveTo(4, 20);
                iconPath.cubicTo(4.5f, 15.5f, 8, 13.5f, 12, 13.5f);
                iconPath.cubicTo(16, 13.5f, 19.5f, 15.5f, 20, 20);
                canvas.drawPath(iconPath, paint);
            } else if ("camera".equals(glyph)) {
                canvas.drawRoundRect(3.5f, 7, 20.5f, 19.5f, 3, 3, paint);
                iconPath.reset();
                iconPath.moveTo(8, 7);
                iconPath.lineTo(9.5f, 4.5f);
                iconPath.lineTo(14.5f, 4.5f);
                iconPath.lineTo(16, 7);
                canvas.drawPath(iconPath, paint);
                canvas.drawCircle(12, 13, 3.4f, paint);
            } else if ("video".equals(glyph)) {
                canvas.drawRoundRect(3.5f, 5.5f, 20.5f, 18.5f, 3, 3, paint);
                iconPath.reset();
                iconPath.moveTo(10, 9);
                iconPath.lineTo(16, 12);
                iconPath.lineTo(10, 15);
                iconPath.close();
                paint.setStyle(Paint.Style.FILL);
                canvas.drawPath(iconPath, paint);
            } else if ("photo".equals(glyph)) {
                canvas.drawRoundRect(3.5f, 5, 20.5f, 19, 3, 3, paint);
                canvas.drawCircle(9, 9.5f, 1.5f, paint);
                iconPath.reset();
                iconPath.moveTo(5.5f, 17);
                iconPath.lineTo(10.5f, 12);
                iconPath.lineTo(13.5f, 14.5f);
                iconPath.lineTo(16, 11);
                iconPath.lineTo(19, 17);
                canvas.drawPath(iconPath, paint);
            }
            canvas.restoreToCount(save);
        }
    }

    private final class FlowerView extends View {
        private final Paint petalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stemPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path stemPath = new Path();
        private final Path leafPath = new Path();
        private final Path secondLeafPath = new Path();
        private final Path petalPath = new Path();
        private int variant;

        FlowerView(Context context) {
            super(context);
            setContentDescription("A little flower");
        }

        void setVariant(int value) {
            variant = value;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int save = canvas.save();
            float side = Math.min(getWidth(), getHeight());
            float scale = side / 64f;
            canvas.translate((getWidth() - side) / 2f, (getHeight() - side) / 2f);
            canvas.scale(scale, scale);

            float centerX = 32;
            float centerY = variant == 1 ? 25 : 23;
            float flowerScale = variant == 1 ? 1f : .76f;
            int petalColor = variant == 2 ? color("#E9B3A7") : color("#E8AFA4");
            int stemColor = color("#91A082");

            stemPaint.setColor(stemColor);
            stemPaint.setStrokeWidth(2.4f);
            stemPaint.setStyle(Paint.Style.STROKE);
            stemPaint.setStrokeCap(Paint.Cap.ROUND);
            stemPath.reset();
            stemPath.moveTo(centerX, centerY + 9 * flowerScale);
            stemPath.cubicTo(centerX + 1, centerY + 20, centerX - 7, 51, centerX - 7, 60);
            canvas.drawPath(stemPath, stemPaint);

            stemPaint.setStyle(Paint.Style.FILL);
            leafPath.reset();
            leafPath.moveTo(28, 46);
            leafPath.cubicTo(18, 38, 17, 47, 28, 49);
            canvas.drawPath(leafPath, stemPaint);
            secondLeafPath.reset();
            secondLeafPath.moveTo(28, 53);
            secondLeafPath.cubicTo(37, 45, 39, 52, 28, 56);
            canvas.drawPath(secondLeafPath, stemPaint);

            petalPaint.setColor(petalColor);
            petalPaint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 6; i++) {
                int petalSave = canvas.save();
                canvas.rotate(i * 60, centerX, centerY);
                petalPath.reset();
                petalPath.moveTo(centerX, centerY);
                petalPath.quadTo(centerX - 9 * flowerScale, centerY - 5 * flowerScale,
                        centerX, centerY - 16 * flowerScale);
                petalPath.quadTo(centerX + 9 * flowerScale, centerY - 5 * flowerScale,
                        centerX, centerY);
                canvas.drawPath(petalPath, petalPaint);
                canvas.restoreToCount(petalSave);
            }
            centerPaint.setColor(color("#EAC57D"));
            centerPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(centerX, centerY, 3.4f * flowerScale, centerPaint);
            canvas.restoreToCount(save);
        }
    }
}
