package com.keepsake.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.OverlaySettings;
import androidx.media3.effect.CanvasOverlay;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.StaticOverlaySettings;
import androidx.media3.effect.TextOverlay;
import androidx.media3.effect.TextureOverlay;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@androidx.media3.common.util.UnstableApi
final class ReelComposer {
    interface Listener {
        void onCompleted();

        void onError(Exception exception);
    }

    private static final long FADE_DURATION_US = 250_000L;
    private static final int OUTPUT_WIDTH = 720;
    private static final int OUTPUT_HEIGHT = 1280;

    private ReelComposer() {}

    static void export(Context context, List<File> images, List<Integer> durationsSeconds,
                       File music, String overlayText, boolean fadeThroughBlack,
                       File output, Listener listener) {
        if (images.size() < 2 || images.size() != durationsSeconds.size()) {
            listener.onError(new IllegalArgumentException("Choose at least two photos."));
            return;
        }

        try {
            List<EditedMediaItem> imageItems = new ArrayList<>();
            for (int i = 0; i < images.size(); i++) {
                long durationMs = Math.max(1, durationsSeconds.get(i)) * 1000L;
                MediaItem mediaItem = new MediaItem.Builder()
                        .setUri(Uri.fromFile(images.get(i)))
                        .setImageDurationMs(durationMs)
                        .build();
                EditedMediaItem.Builder edited = new EditedMediaItem.Builder(mediaItem)
                        .setFrameRate(30);
                if (fadeThroughBlack) {
                    edited.setEffects(new Effects(Collections.emptyList(), Collections.singletonList(
                            new OverlayEffect(Collections.singletonList(
                                    new TimedBlackOverlay(durationMs * 1000L))))));
                }
                imageItems.add(edited.build());
            }

            EditedMediaItemSequence videoSequence = new EditedMediaItemSequence.Builder(
                    Collections.singleton(C.TRACK_TYPE_VIDEO))
                    .addItems(imageItems)
                    .build();

            List<androidx.media3.common.Effect> compositionEffects = new ArrayList<>();
            compositionEffects.add(Presentation.createForWidthAndHeight(
                    OUTPUT_WIDTH, OUTPUT_HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP));
            if (overlayText != null && !overlayText.trim().isEmpty()) {
                SpannableString styledText = new SpannableString(overlayText.trim());
                styledText.setSpan(new ForegroundColorSpan(Color.WHITE), 0, styledText.length(), 0);
                styledText.setSpan(new BackgroundColorSpan(Color.argb(175, 31, 39, 32)),
                        0, styledText.length(), 0);
                styledText.setSpan(new StyleSpan(Typeface.BOLD), 0, styledText.length(), 0);
                StaticOverlaySettings textSettings = new StaticOverlaySettings.Builder()
                        .setBackgroundFrameAnchor(0f, -0.74f)
                        .setOverlayFrameAnchor(0f, 0f)
                        .setScale(1f, 1f)
                        .build();
                TextureOverlay textOverlay = TextOverlay.createStaticTextOverlay(styledText, textSettings);
                compositionEffects.add(new OverlayEffect(Collections.singletonList(textOverlay)));
            }

            List<EditedMediaItemSequence> sequences = new ArrayList<>();
            sequences.add(videoSequence);
            if (music != null && music.isFile()) {
                EditedMediaItem soundtrack = new EditedMediaItem.Builder(
                        MediaItem.fromUri(Uri.fromFile(music)))
                        .setRemoveVideo(true)
                        .build();
                sequences.add(new EditedMediaItemSequence.Builder(
                        Collections.singleton(C.TRACK_TYPE_AUDIO))
                        .addItem(soundtrack)
                        .setIsLooping(true)
                        .build());
            }

            Composition composition = new Composition.Builder(sequences)
                    .setEffects(new Effects(Collections.emptyList(), compositionEffects))
                    .build();
            Transformer transformer = new Transformer.Builder(context)
                    .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
                    .setAudioMimeType(androidx.media3.common.MimeTypes.AUDIO_AAC)
                    .addListener(new Transformer.Listener() {
                        @Override
                        public void onCompleted(Composition completed, ExportResult result) {
                            listener.onCompleted();
                        }

                        @Override
                        public void onError(Composition failed, ExportResult result,
                                            ExportException exception) {
                            listener.onError(exception);
                        }
                    })
                    .build();
            transformer.start(composition, output.getAbsolutePath());
        } catch (Exception exception) {
            listener.onError(exception);
        }
    }

    private static final class TimedBlackOverlay extends CanvasOverlay {
        private final long durationUs;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        TimedBlackOverlay(long durationUs) {
            super(true);
            this.durationUs = durationUs;
            paint.setColor(Color.BLACK);
        }

        @Override
        public void onDraw(Canvas canvas, long presentationTimeUs) {
            long fadeUs = Math.min(FADE_DURATION_US, durationUs / 2);
            long remainingUs = Math.max(0, durationUs - presentationTimeUs);
            float fadeIn = 1f - Math.min(1f, presentationTimeUs / (float) Math.max(1, fadeUs));
            float fadeOut = 1f - Math.min(1f, remainingUs / (float) Math.max(1, fadeUs));
            paint.setAlpha(Math.round(Math.max(fadeIn, fadeOut) * 255f));
            canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), paint);
        }

        @Override
        public OverlaySettings getOverlaySettings(long presentationTimeUs) {
            return new StaticOverlaySettings.Builder().build();
        }
    }
}