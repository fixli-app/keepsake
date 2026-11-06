package com.keepsake.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

final class PostStore extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "keepsake.db";
    private static final int DATABASE_VERSION = 3;

    PostStore(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE albums (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL COLLATE NOCASE UNIQUE, emoji TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)");
        database.execSQL("CREATE TABLE posts (id INTEGER PRIMARY KEY AUTOINCREMENT, file_name TEXT NOT NULL, media_type TEXT NOT NULL, caption TEXT NOT NULL, created_at INTEGER NOT NULL, album_id INTEGER REFERENCES albums(id) ON DELETE SET NULL)");
        database.execSQL("CREATE TABLE comments (id INTEGER PRIMARY KEY AUTOINCREMENT, post_id INTEGER NOT NULL REFERENCES posts(id) ON DELETE CASCADE, text TEXT NOT NULL, created_at INTEGER NOT NULL)");
        database.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            database.execSQL("CREATE TABLE albums (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL COLLATE NOCASE UNIQUE, created_at INTEGER NOT NULL)");
            database.execSQL("ALTER TABLE posts ADD COLUMN album_id INTEGER REFERENCES albums(id) ON DELETE SET NULL");
        }
        if (oldVersion < 3) {
            database.execSQL("ALTER TABLE albums ADD COLUMN emoji TEXT NOT NULL DEFAULT ''");
            database.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        }
    }

    @Override
    public void onConfigure(SQLiteDatabase database) {
        database.setForeignKeyConstraintsEnabled(true);
    }

    long addPost(String fileName, String mediaType, String caption, long albumId) {
        ContentValues values = new ContentValues();
        values.put("file_name", fileName);
        values.put("media_type", mediaType);
        values.put("caption", caption);
        values.put("created_at", System.currentTimeMillis());
        if (albumId >= 0) {
            values.put("album_id", albumId);
        }
        return getWritableDatabase().insertOrThrow("posts", null, values);
    }

    List<Post> getPosts() {
        List<Post> posts = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT posts.id, posts.file_name, posts.media_type, posts.caption, posts.created_at, " +
                        "posts.album_id, albums.name FROM posts LEFT JOIN albums ON albums.id = posts.album_id " +
                        "ORDER BY posts.created_at DESC", null)) {
            while (cursor.moveToNext()) {
                posts.add(new Post(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getLong(4),
                        cursor.isNull(5) ? -1 : cursor.getLong(5),
                        cursor.getString(6)));
            }
        }
        return posts;
    }

    List<Album> getAlbums() {
        List<Album> albums = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("albums",
                new String[]{"id", "name"}, null, null, null, null, "name COLLATE NOCASE ASC")) {
            while (cursor.moveToNext()) {
                albums.add(new Album(cursor.getLong(0), cursor.getString(1)));
            }
        }
        return albums;
    }

    long addAlbum(String name) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insertOrThrow("albums", null, values);
    }

    void updateAlbum(long albumId, String name, String emoji) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("emoji", emoji);
        getWritableDatabase().update("albums", values, "id = ?", new String[]{Long.toString(albumId)});
    }

    String getAlbumEmoji(long albumId) {
        try (Cursor cursor = getReadableDatabase().query("albums", new String[]{"emoji"},
                "id = ?", new String[]{Long.toString(albumId)}, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getString(0) : "";
        }
    }

    long addNote(String text) {
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("text", text);
        values.put("created_at", now);
        values.put("updated_at", now);
        return getWritableDatabase().insertOrThrow("notes", null, values);
    }

    void updateNote(long noteId, String text) {
        ContentValues values = new ContentValues();
        values.put("text", text);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("notes", values, "id = ?", new String[]{Long.toString(noteId)});
    }

    void deleteNote(long noteId) {
        getWritableDatabase().delete("notes", "id = ?", new String[]{Long.toString(noteId)});
    }

    List<Note> getNotes() {
        List<Note> notes = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("notes",
                new String[]{"id", "text", "updated_at"}, null, null, null, null,
                "updated_at DESC, id DESC")) {
            while (cursor.moveToNext()) {
                notes.add(new Note(cursor.getLong(0), cursor.getString(1), cursor.getLong(2)));
            }
        }
        return notes;
    }

    void setPostAlbum(long postId, long albumId) {
        ContentValues values = new ContentValues();
        if (albumId >= 0) {
            values.put("album_id", albumId);
        } else {
            values.putNull("album_id");
        }
        getWritableDatabase().update("posts", values, "id = ?", new String[]{Long.toString(postId)});
    }

    void updateCaption(long postId, String caption) {
        ContentValues values = new ContentValues();
        values.put("caption", caption);
        getWritableDatabase().update("posts", values, "id = ?", new String[]{Long.toString(postId)});
    }

    void deletePost(long postId) {
        getWritableDatabase().delete("posts", "id = ?", new String[]{Long.toString(postId)});
    }

    void addComment(long postId, String text) {
        ContentValues values = new ContentValues();
        values.put("post_id", postId);
        values.put("text", text);
        values.put("created_at", System.currentTimeMillis());
        getWritableDatabase().insertOrThrow("comments", null, values);
    }

    List<Comment> getComments(long postId) {
        List<Comment> comments = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("comments",
                new String[]{"id", "text", "created_at"}, "post_id = ?", new String[]{Long.toString(postId)},
                null, null, "created_at ASC")) {
            while (cursor.moveToNext()) {
                comments.add(new Comment(cursor.getLong(0), cursor.getString(1), cursor.getLong(2)));
            }
        }
        return comments;
    }

    void updateComment(long commentId, String text) {
        ContentValues values = new ContentValues();
        values.put("text", text);
        getWritableDatabase().update("comments", values, "id = ?", new String[]{Long.toString(commentId)});
    }

    void deleteComment(long commentId) {
        getWritableDatabase().delete("comments", "id = ?", new String[]{Long.toString(commentId)});
    }

    int getCommentCount(long postId) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM comments WHERE post_id = ?", new String[]{Long.toString(postId)})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    static final class Post {
        final long id;
        final String fileName;
        final String mediaType;
        final String caption;
        final long createdAt;
        final long albumId;
        final String albumName;

        Post(long id, String fileName, String mediaType, String caption, long createdAt,
             long albumId, String albumName) {
            this.id = id;
            this.fileName = fileName;
            this.mediaType = mediaType;
            this.caption = caption;
            this.createdAt = createdAt;
            this.albumId = albumId;
            this.albumName = albumName;
        }
    }

    static final class Album {
        final long id;
        final String name;

        Album(long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static final class Note {
        final long id;
        final String text;
        final long updatedAt;

        Note(long id, String text, long updatedAt) {
            this.id = id;
            this.text = text;
            this.updatedAt = updatedAt;
        }
    }

    static final class Comment {
        final long id;
        final String text;
        final long createdAt;

        Comment(long id, String text, long createdAt) {
            this.id = id;
            this.text = text;
            this.createdAt = createdAt;
        }
    }
}
