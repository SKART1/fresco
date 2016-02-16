/*
 * This file provided by Facebook is for non-commercial testing and evaluation
 * purposes only.  Facebook reserves all rights not expressly granted.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * FACEBOOK BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.facebook.samples.redownloadingapp;

import java.util.HashSet;
import java.util.Set;

import android.app.Activity;
import android.graphics.drawable.Animatable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.facebook.common.logging.FLog;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.controller.ControllerListener;
import com.facebook.drawee.drawable.ProgressBarDrawable;
import com.facebook.drawee.generic.GenericDraweeHierarchy;
import com.facebook.drawee.generic.GenericDraweeHierarchyBuilder;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.SimpleDraweeView;
import com.facebook.imagepipeline.cache.DiskCache;
import com.facebook.imagepipeline.cache.DiskCacheInterface;
import com.facebook.imagepipeline.core.ImagePipeline;
import com.facebook.imagepipeline.core.ImagePipelineConfig;
import com.facebook.imagepipeline.image.ImageInfo;
import com.facebook.imagepipeline.listener.RequestListener;
import com.facebook.imagepipeline.listener.RequestLoggingListener;
import com.facebook.imagepipeline.producers.ResumeDownloadFetcher;


public class MainActivity extends Activity {
  private SimpleDraweeView mSimpleDraweeView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    FLog.setMinimumLoggingLevel(FLog.VERBOSE);
    Set<RequestListener> listeners = new HashSet<>();
    listeners.add(new RequestLoggingListener());

    final DiskCacheInterface diskCache = new DiskCacheInterface.DumbDiskCahce();//DiskCache.getInstance(this);

    ImagePipelineConfig config = ImagePipelineConfig.newBuilder(this)
            .setNetworkFetcher(new ResumeDownloadFetcher(diskCache))
            .setRequestListeners(listeners)
            .build();
    Fresco.initialize(this, config);
    setContentView(R.layout.activity_main);

    mSimpleDraweeView = (SimpleDraweeView) findViewById(R.id.simple_drawee_view);


    final EditText editText = (EditText) findViewById(R.id.uri_edit_text);
    Button downloadButton = (Button) findViewById(R.id.download_button);
    Button clearCacheButton = (Button) findViewById(R.id.clear_cache_button);

    editText.setText("https://upload.wikimedia.org/wikipedia/commons/6/61/Flat_earth_night.png");

    downloadButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        updateImageUri(Uri.parse(editText.getText().toString()));
      }
    });

    clearCacheButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        ImagePipeline imagePipeline = Fresco.getImagePipeline();

        imagePipeline.clearCaches();

        diskCache.clearCache();
      }
    });
  }

  private void updateImageUri(Uri uri) {
    DraweeController controller = Fresco.newDraweeControllerBuilder()
            .setUri(uri)
            .setAutoPlayAnimations(true)
            .setControllerListener(new ControllerListener<ImageInfo>() {
              @Override
              public void onSubmit(String id, Object callerContext) {
                Toast.makeText(MainActivity.this, "onSubmit", Toast.LENGTH_SHORT).show();
              }

              @Override
              public void onFinalImageSet(String id, @Nullable ImageInfo imageInfo, @Nullable Animatable animatable) {
                Toast.makeText(MainActivity.this, "onFinalImageSet", Toast.LENGTH_SHORT).show();
              }

              @Override
              public void onIntermediateImageSet(String id, @Nullable ImageInfo imageInfo) {
                Toast.makeText(MainActivity.this, "onIntermediateImageSet", Toast.LENGTH_SHORT).show();
              }

              @Override
              public void onIntermediateImageFailed(String id, Throwable throwable) {
                Toast.makeText(MainActivity.this, "onIntermediateImageFailed", Toast.LENGTH_SHORT).show();
              }

              @Override
              public void onFailure(String id, Throwable throwable) {
                Toast.makeText(MainActivity.this, "onFailure", Toast.LENGTH_SHORT).show();
              }

              @Override
              public void onRelease(String id) {
                Toast.makeText(MainActivity.this, "onRelease", Toast.LENGTH_SHORT).show();
              }
            })
            .build();

    GenericDraweeHierarchy hierarchy = new GenericDraweeHierarchyBuilder(getResources())
            .setProgressBarImage(new ProgressBarDrawable())
            .setFailureImage(getResources().getDrawable(R.drawable.error))
            .build();

    mSimpleDraweeView.setHierarchy(hierarchy);

    mSimpleDraweeView.setHierarchy(hierarchy);
    mSimpleDraweeView.setController(controller);
  }
}
