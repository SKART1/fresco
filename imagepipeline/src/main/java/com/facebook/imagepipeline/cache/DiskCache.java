package com.facebook.imagepipeline.cache;

import android.content.Context;
import android.support.annotation.NonNull;

import com.facebook.common.logging.FLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class DiskCache implements DiskCacheInterface {
  private static final String DISK_CACHE_SUBDIR = "intermediate";
  public static final String TAG = DiskCache.class.getName();

  //Singleton
  private static volatile DiskCache diskCache = null;

  private Semaphore writeLock = new Semaphore(1);
  private final ConcurrentHashMap<Integer, Boolean> fileLocks = new ConcurrentHashMap<>(6);

  private File cacheDir;
  private final int maximumSimultaneouslyFilesInCache;

  public static void initInstance(Context context, int maximumSimultaneouslyFilesInCache) {
    if (diskCache == null) {
      synchronized (DiskCache.class) {
        if (diskCache == null) {
          diskCache = new DiskCache(context, maximumSimultaneouslyFilesInCache);
        }
      }
    }
  }

  public static DiskCache getInstance() {
    if (diskCache == null) {
      throw new IllegalStateException("initInstance should be called before getInstance");
    }
    return diskCache;
  }

  private DiskCache(Context context, int maximumSimultaneouslyFilesInCache) {
    this.maximumSimultaneouslyFilesInCache = maximumSimultaneouslyFilesInCache;

    cacheDir = new File(context.getCacheDir(), DISK_CACHE_SUBDIR);
    cacheDir.mkdirs();

    //Init blocker
    writeLock.acquireUninterruptibly();

    File[] files = cacheDir.listFiles();
    for (File file : files) {
      if (!file.isDirectory()) {
        fileLocks.put(file.getName().hashCode(), false);
      }
    }

    writeLock.release();
  }


  @NonNull
  @Override
  public CacheInfo getCacheInfo(String url) {
    CacheInfo cacheInfo = new CacheInfo();
    int fileName = url.hashCode();

    cacheInfo.setFileName(fileName);

    //
    File fileInp = new File(cacheDir, String.valueOf(fileName));


    writeLock.acquireUninterruptibly();
    if (fileInp.exists()) {
      Boolean isProcessing = fileLocks.putIfAbsent(fileName, false);

      //Somebody have taken this file before us
      if (isProcessing != null && isProcessing) {
        FLog.e(TAG, "File already locked by readers!");
        cacheInfo.setFileOffset(CacheInfo.NO_OFFSET);
        cacheInfo.setFile(null);
      } else {
        if (keepCacheSmall()) {
          fileLocks.put(cacheInfo.getFileName(), true);
          cacheInfo.setFileOffset(fileInp.length());
          cacheInfo.setFile(fileInp);
        } else {
          FLog.e(TAG, "Cache is full");
          cacheInfo.setFileOffset(0);
          cacheInfo.setFile(null);
        }
      }

    } else {
      //Who first created file - is his master
      cacheInfo.setFileOffset(0);
      cacheInfo.setFile(fileInp);
      fileLocks.put(cacheInfo.getFileName(), true);
    }
    writeLock.release();

    return cacheInfo;
  }

  /**
   * Removes somebody from cache if necessary. DO NOT CALL IT WITHOUT SYNCHRONIZING ON {@link #writeLock}
   * @return true if there is space in cache, false if cache is full
   */
  private boolean keepCacheSmall() {
    if(fileLocks.size() < maximumSimultaneouslyFilesInCache) {
      return true;
    } else {
      for(Map.Entry<Integer, Boolean> fileLock: fileLocks.entrySet()) {
        if(!fileLock.getValue()) {
          File fileInp = new File(cacheDir, String.valueOf(fileLock.getValue()));
          if (fileInp.delete()) {
            fileLocks.remove(fileLock.getKey());
            return false;
          }
        }
      }
      return false;
    }
  }

  @Override
  public InputStream getInputStream(@NonNull CacheInfo cacheInfo) {
    InputStream is = null;
    try {
      File file = cacheInfo.getFile();
      if (file != null) {
        if (!file.exists()) {
          if (file.createNewFile()) {
            is = new FileInputStream(file);
          }
        } else {
          is = new FileInputStream(file);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return is;
  }

  @Override
  public OutputStream getOutputStream(@NonNull CacheInfo cacheInfo) {
    FileOutputStream fileOutputStream = null;

    try {
      File file = cacheInfo.getFile();
      if (file != null) {
        if (!file.exists()) {
          if (file.createNewFile()) {
            fileOutputStream = new FileOutputStream(cacheInfo.getFile(), true);
          }
        } else {
          fileOutputStream = new FileOutputStream(cacheInfo.getFile(), true);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return fileOutputStream;
  }

  @Override
  public void clearCache() {
    deleteAllInsideRecursively(cacheDir);
  }

  @Override
  public void onFinished(CacheInfo cacheInfo) {
    if (cacheInfo.getFile() != null) {
      writeLock.acquireUninterruptibly();

      if (cacheInfo.getFile().delete()) {
        fileLocks.remove(cacheInfo.getFileName());
      } else {
        //If file were not even created
        if(!cacheInfo.getFile().exists()) {
          fileLocks.remove(cacheInfo.getFileName());
        }
      }

      writeLock.release();
    }
  }

  @Override
  public void onError(CacheInfo cacheInfo, Throwable throwable) {
    writeLock.acquireUninterruptibly();

    fileLocks.put(cacheInfo.getFileName(), false);
    writeLock.release();
  }

  /**
   * Deletes the given folder and all its files / subfolders.
   * Is not implemented in a recursive way. The "Recursively" in the name stems from the filesystem command
   *
   * @param root The folder to delete recursively
   */
  public static void deleteAllInsideRecursively(final File root) {
    LinkedList<File> deletionQueue = new LinkedList<>();
    deletionQueue.add(root);

    while (!deletionQueue.isEmpty()) {
      final File toDelete = deletionQueue.removeFirst();
      final File[] children = toDelete.listFiles();

      if (children == null || children.length == 0) {
        // This is either a file or an empty directory -> deletion possible
        if (toDelete != root) {
          toDelete.delete();
        }
      } else {
        // Add the children before the folder because they have to be deleted first
        deletionQueue.addAll(Arrays.asList(children));
        // Add the folder again because we can't delete it yet.
        deletionQueue.addLast(toDelete);
      }
    }
  }
}



