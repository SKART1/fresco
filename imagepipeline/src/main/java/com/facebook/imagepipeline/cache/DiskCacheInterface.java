package com.facebook.imagepipeline.cache;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * DiskCacheInterface is responsible for storing, managing and cleaning intermediate downloaded progress
 * locally and providing all necessary information about it. There are some things that you should
 * store in your mind when implementing it:
 *  * One file must give one output stream
 *  * One file must always give one output stream - seriously, dude, if two streams will open same
 *    file ugly things may happen. Multiply outputStreams have shared position in file, so if writing batches
 *    after batches to multiply output streams there will be mess in output
 *    (nevertheless Java provide with atomic write).
 *  * Provide correct file size
 *  * Closing files is also important.
 */
public interface DiskCacheInterface {
  @NonNull CacheInfo getCacheInfo(String url);

  InputStream getInputStream(CacheInfo cacheInfo);

  OutputStream getOutputStream(CacheInfo cacheInfo);

  void clearCache();

  void onFinished(CacheInfo cacheInfo);

  void onError(CacheInfo cacheInfo, Throwable throwable);

  class CacheInfo {
    public static final int NO_OFFSET = -7;
    public static final int NO_FILENAME = -8;

    private int fileName;
    private long fileOffset;
    private @Nullable File file;

    public CacheInfo() {
      fileName = NO_FILENAME;
      fileOffset = NO_OFFSET;
      file = null;
    }

    public int getFileName() {
      return fileName;
    }

    public void setFileName(int fileName) {
      this.fileName = fileName;
    }

    public long getFileOffset() {
      return fileOffset;
    }

    public void setFileOffset(long fileOffset) {
      this.fileOffset = fileOffset;
    }

    @Nullable
    public File getFile() {
      return file;
    }

    public void setFile(@Nullable File  file) {
      this.file = file;
    }
  }


  /**
   * Cache which do nothing but still works!
   */
  class DumbDiskCache implements DiskCacheInterface {
    @NonNull
    @Override
    public CacheInfo getCacheInfo(String url) {
      return new CacheInfo();
    }

    @Override
    public InputStream getInputStream(CacheInfo cacheInfo) {
      return null;
    }

    @Override
    public OutputStream getOutputStream(CacheInfo cacheInfo) {
      return null;
    }

    @Override
    public void clearCache() {

    }

    @Override
    public void onFinished(CacheInfo cacheInfo) {

    }

    @Override
    public void onError(CacheInfo cacheInfo, Throwable throwable) {

    }
  }
}
