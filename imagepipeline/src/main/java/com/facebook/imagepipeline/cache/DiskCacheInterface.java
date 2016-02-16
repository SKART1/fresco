package com.facebook.imagepipeline.cache;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

public interface DiskCacheInterface {
  CacheInfo getCacheInfo(String url);

  InputStream getInputStream(CacheInfo cacheInfo);

  OutputStream getOutputStream(CacheInfo cacheInfo);

  void clearCache();

  void onFinished(CacheInfo cacheInfo);

  void onError(CacheInfo cacheInfo, Throwable throwable);

  class CacheInfo {
    public static final int NO_OFFSET = -7;
    public static final int NO_FILENAME = -8;

    private int fileName;
    private int fileOffset;
    private File file;

    public CacheInfo() {
      fileName = NO_OFFSET;
      fileOffset = NO_FILENAME;
      file = null;
    }

    public int getFileName() {
      return fileName;
    }

    public void setFileName(int fileName) {
      this.fileName = fileName;
    }

    public int getFileOffset() {
      return fileOffset;
    }

    public void setFileOffset(int fileOffset) {
      this.fileOffset = fileOffset;
    }

    public File getFile() {
      return file;
    }

    public void setFile(File file) {
      this.file = file;
    }
  }


  public static class DumbDiskCahce implements DiskCacheInterface {
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
