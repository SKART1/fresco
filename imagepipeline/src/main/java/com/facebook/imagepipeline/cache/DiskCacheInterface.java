package com.facebook.imagepipeline.cache;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

public interface DiskCacheInterface {
  InfoStruct getFile(String url);

  InputStream getInputStream(InfoStruct infoStruct);

  OutputStream getOutputStream(InfoStruct infoStruct);

  void clearCache();

  void onFinished(InfoStruct infoStruct);

  void onError(InfoStruct infoStruct, Throwable throwable);

  class InfoStruct {
    private int fileName;
    private int fileOffset;
    private File file;

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
    public InfoStruct getFile(String url) {
      return null;
    }

    @Override
    public InputStream getInputStream(InfoStruct infoStruct) {
      return null;
    }

    @Override
    public OutputStream getOutputStream(InfoStruct infoStruct) {
      return null;
    }

    @Override
    public void clearCache() {

    }

    @Override
    public void onFinished(InfoStruct infoStruct) {

    }

    @Override
    public void onError(InfoStruct infoStruct, Throwable throwable) {

    }
  }
}
