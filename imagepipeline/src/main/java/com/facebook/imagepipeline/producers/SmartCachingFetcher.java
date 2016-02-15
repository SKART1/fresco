package com.facebook.imagepipeline.producers;

import android.net.Uri;

import com.facebook.common.logging.FLog;
import com.facebook.imagepipeline.cache.DiskCache;
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
 * Created by art on 2/4/16.
 */
public class SmartCachingFetcher extends BaseNetworkFetcher<FetchState> {
  private static final String TAG = SmartCachingFetcher.class.getName();

  private static final int NUM_NETWORK_THREADS = 3;

  private final ExecutorService mExecutorService;
  private DiskCache diskCache;


  public SmartCachingFetcher(DiskCache diskCache) {
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

                DiskCacheInterface.InfoStruct infoStruct = diskCache.getFile(uriString);

                while (true) {
                  String nextUriString;
                  String nextScheme;

                  try {
                    final String rangeHeader = "bytes=" + infoStruct.getFileOffset() + "-";
                    FLog.w(TAG, "Trying offset value: " + infoStruct.getFileOffset());

                    URL url = new URL(uriString);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestProperty("Range", rangeHeader);

                    nextUriString = connection.getHeaderField("Location");
                    nextScheme = (nextUriString == null) ? null : Uri.parse(nextUriString).getScheme();

                    if (nextUriString == null || nextScheme.equals(scheme)) {
                      int contentLengthValue = -1;

                      String acceptRanges = connection.getHeaderField("Accept-Ranges");
                      InputStream is;
                      //If response is 206 (allows partitioning)
                      if (connection.getResponseCode() == HttpURLConnection.HTTP_PARTIAL) {

                        firstInputStream = diskCache.getInputStream(infoStruct);
                        secondInputStream = connection.getInputStream();

                        outputStream = diskCache.getOutputStream(infoStruct);
                        is = new DoubleSourceStream(firstInputStream, secondInputStream, outputStream);
                      } else {
                        is = connection.getInputStream();
                      }

                      //Content-Length header parsing
                      String contentLengthHeader = connection.getHeaderField("Content-Length");
                      if (contentLengthHeader != null) {
                        contentLengthValue = Integer.parseInt(contentLengthHeader);
                      }

                      callback.onResponse(is, contentLengthValue);
                      diskCache.onFinished(infoStruct);
                      break;
                    }
                    uriString = nextUriString;
                    scheme = nextScheme;
                  } catch (Exception e) {
                    diskCache.onError(infoStruct, e);
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

