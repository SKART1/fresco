package com.facebook.imagepipeline.producers;

import android.net.Uri;

import com.facebook.common.logging.FLog;
import com.facebook.imagepipeline.cache.DiskCacheInterface;
import com.facebook.imagepipeline.image.EncodedImage;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * This class is a special fetcher which will try:
 *    * When new request come, fetcher asks #DiskCacheInterface if any intermediate progress is saved
 *      locally. #DiskCacheInterface provide with all necessary information about previous progress,
 *      allowing this fetcher to set required HTTP v1.1+ headers to continue just from the same position
 *      it have stop before. Fetcher deals with cases when server do not support download resuming -
 *      its just returns standard #InputStream. But when servers response it support such thing - then
 *      all the stuff is done :)
 *      Using #DoubleSourceStream information fetched from internet will be saved in output stream
 *
 *    * Save intermediate download progress in #OutputStream provided by #DiskCacheInterface.
 *      While downloading media from internet it`s bytes will be appended just at the end of #OutputStream.
 *      Actually this action is done by {@see #DoubleSourceStream}. About cache which provide streams and manage files see
 *      interface {@see #com.facebook.imagepipeline.cache.DiskCacheInterface} and its implementation
 *      {@see #com.facebook.imagepipeline.cache.DiskCache} and {@see #com.facebook.imagepipeline.cache.DiskCacheInterface.DumbDiskCache}.
 *
 */
public class ResumeDownloadFetcher extends BaseNetworkFetcher<FetchState> {
  private static final String TAG = ResumeDownloadFetcher.class.getName();

  private static final int NUM_NETWORK_THREADS = 3;

  private final ExecutorService mExecutorService;
  private DiskCacheInterface diskCache;


  public ResumeDownloadFetcher(DiskCacheInterface diskCache) {
    this.diskCache = diskCache;
    mExecutorService = Executors.newFixedThreadPool(NUM_NETWORK_THREADS);
  }

  @Override
  public FetchState createFetchState(Consumer<EncodedImage> consumer, ProducerContext context) {
    return new FetchState(consumer, context);
  }

  @Override
  public void fetch(final FetchState fetchState, final Callback callback) {
    final Future<?> future = mExecutorService.submit(
            new Runnable() {
              @Override
              public void run() {
                HttpURLConnection connection = null;

                InputStream firstInputStream;
                InputStream secondInputStream;
                OutputStream outputStream;

                Uri uri = fetchState.getUri();
                String scheme = uri.getScheme();
                String uriString = fetchState.getUri().toString();

                DiskCacheInterface.CacheInfo cacheInfo = diskCache.getCacheInfo(uriString);

                while (true) {
                  String nextUriString;
                  String nextScheme;

                  try {
                    URL url = new URL(uriString);
                    connection = (HttpURLConnection) url.openConnection();

                    if(cacheInfo.getFileOffset() != DiskCacheInterface.CacheInfo.NO_OFFSET) {
                      FLog.w(TAG, "Trying offset value: " + cacheInfo.getFileOffset());

                      final  String rangeHeader = "bytes=" + cacheInfo.getFileOffset() + "-";
                      connection.setRequestProperty("Range", rangeHeader);
                    } else {
                      FLog.w(TAG, "No offset value");
                    }

                    nextUriString = connection.getHeaderField("Location");
                    nextScheme = (nextUriString == null) ? null : Uri.parse(nextUriString).getScheme();

                    if (nextUriString == null || nextScheme.equals(scheme)) {
                      int contentLengthValue = -1;

                      String acceptRanges = connection.getHeaderField("Accept-Ranges");
                      InputStream is;
                      //If response is 206 (allows partitioning)
                      if (connection.getResponseCode() == HttpURLConnection.HTTP_PARTIAL) {

                        firstInputStream = diskCache.getInputStream(cacheInfo);
                        secondInputStream = connection.getInputStream();

                        outputStream = diskCache.getOutputStream(cacheInfo);
                        is = new DoubleSourceStream(firstInputStream, cacheInfo.getFileOffset(),
                                secondInputStream, outputStream);
                      } else {
                        is = connection.getInputStream();
                      }

                      //Content-Length header parsing
                      String contentLengthHeader = connection.getHeaderField("Content-Length");
                      if (contentLengthHeader != null) {
                        contentLengthValue = Integer.parseInt(contentLengthHeader);
                      }

                      callback.onResponse(is, contentLengthValue);
                      diskCache.onFinished(cacheInfo);
                      break;
                    }
                    uriString = nextUriString;
                    scheme = nextScheme;
                  } catch (Exception e) {
                    diskCache.onError(cacheInfo, e);
                    callback.onFailure(e);
                    break;
                  } finally {

                    if (connection != null) {
                      connection.disconnect();
                    }
                  }
                }

              }
            });
    fetchState.getContext().addCallbacks(
            new BaseProducerContextCallbacks() {
              @Override
              public void onCancellationRequested() {
                if (future.cancel(false)) {
                  callback.onCancellation();
                }
              }
            });
  }

}

