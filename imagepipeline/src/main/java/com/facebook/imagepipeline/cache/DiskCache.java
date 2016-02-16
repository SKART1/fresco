package com.facebook.imagepipeline.cache;

import android.content.Context;
import android.support.annotation.Nullable;

import com.facebook.common.logging.FLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Created by art on 2/5/16.
 */
public class DiskCache implements DiskCacheInterface {
  private static final String DISK_CACHE_SUBDIR = "thumbnails";

  //Singleton
  private static volatile DiskCache diskCache = null;

  private Semaphore writeLock = new Semaphore(1);
  private final ConcurrentHashMap<Integer, Boolean> concurrentHashMap = new ConcurrentHashMap<>(6);

  private File cacheDir;

  public static DiskCache getInstance(Context context) {
    if (diskCache == null) {
      synchronized (DiskCache.class) {
        if (diskCache == null) {
          diskCache = new DiskCache(context);
        }
      }
    }
    return diskCache;
  }

  private DiskCache(Context context) {
    cacheDir = new File(context.getCacheDir(), DISK_CACHE_SUBDIR);
    cacheDir.mkdirs();

    //Init blocker
    try {
      writeLock.acquire();

      File[] files = cacheDir.listFiles();
      for (File file : files) {
        if (!file.isDirectory()) {
          concurrentHashMap.putIfAbsent(file.getName().hashCode(), false);
        }
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      writeLock.release();
    }
  }


  @Override
  public CacheInfo getCacheInfo(String url) {
    CacheInfo cacheInfo = new CacheInfo();

    cacheInfo.setFileName(url.hashCode());

    //
    File fileInp = new File(cacheDir, String.valueOf(cacheInfo.getFileName()));


    if(fileInp.length() < Integer.MAX_VALUE) {
      cacheInfo.setFileOffset((int) fileInp.length());
    } else {
      cacheInfo.setFileOffset(0);
    }

    cacheInfo.setFile(fileInp);

    return cacheInfo;
  }

  @Override
  public InputStream getInputStream(CacheInfo cacheInfo) {
    if(cacheInfo == null){
      return null;
    }

    InputStream is = null;
    try {
      File file = cacheInfo.getFile();
      if (!file.exists()) {
        if (file.createNewFile()) {
          is = new FileInputStream(file);
        }
      } else {
        is = new FileInputStream(file);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return is;
  }

  @Override
  public OutputStream getOutputStream(CacheInfo cacheInfo) {
    FileOutputStream fileOutputStream = null;

    if(cacheInfo == null){
      return null;
    }

    try {
      File file = cacheInfo.getFile();
      if (!file.exists()) {
        if (file.createNewFile()) {
          fileOutputStream = getOutputStreamMonopole(cacheInfo);
        }
      } else {
        fileOutputStream = getOutputStreamMonopole(cacheInfo);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return fileOutputStream;
  }

  @Nullable
  private FileOutputStream getOutputStreamMonopole(CacheInfo cacheInfo) throws FileNotFoundException {
    try {
      writeLock.acquire();
      Boolean isProcessing = concurrentHashMap.putIfAbsent(cacheInfo.getFileName(), false);

      if (isProcessing != null && isProcessing) {
        FLog.e(DiskCache.class.getName(), " Multiply writers to one file!");
      } else {
        return new FileOutputStream(cacheInfo.getFile(), true);
      }

    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      writeLock.release();
    }
    return null;
  }


  @Override
  public void clearCache() {
    deleteAllInsideRecursively(cacheDir);
  }

  @Override
  public void onFinished(CacheInfo cacheInfo) {
    try {
      writeLock.acquire();

      if (cacheInfo.getFile().delete()) {
        concurrentHashMap.remove(cacheInfo.getFileName());
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      writeLock.release();
    }
  }

  @Override
  public void onError(CacheInfo cacheInfo, Throwable throwable) {
    try {
      writeLock.acquire();

      concurrentHashMap.put(cacheInfo.getFileName(), false);
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      writeLock.release();
    }
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



