package com.facebook.imagepipeline.producers;

import android.support.annotation.NonNull;

import com.facebook.common.logging.FLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Class reads from first {@link java.io.InputStream} and when it ends
 * continues to read from second {@link java.io.InputStream}
 */
public class DoubleSourceStream extends InputStream {
  private static final String TAG = DoubleSourceStream.class.getName();

  private InputStream firstInputStream;
  private InputStream secondInputStream;

  private OutputStream firstOutputStream;

  private boolean firstEnds = false;
  private int firstInputStreamTotalRead = 0;
  private int secondInputStreamTotalRead = 0;

  public DoubleSourceStream(InputStream firstInputStream,
                            @NonNull InputStream secondInputStream,
                            OutputStream firstOutputStream) {
    this.firstInputStream = firstInputStream;
    this.secondInputStream = secondInputStream;

    this.firstOutputStream = firstOutputStream;

    if (this.firstInputStream == null) {
      firstEnds = true;
    }
  }

  @Override
  public void close() throws IOException {
    try {
      firstInputStream.close();
    } finally {
      try {
        if (secondInputStream != null) {
          secondInputStream.close();
        }
      } finally {
        if (firstOutputStream != null) {
          firstOutputStream.close();
        }
      }
    }
  }

  @Override
  public int available() throws IOException {
    if (!firstEnds) {
      return firstInputStream.available();
    } else if (secondInputStream != null) {
      return secondInputStream.available();
    }
    return 0;
  }

  @Override
  public int read() throws IOException {
    int value;

    if (!firstEnds) {
      value = firstInputStream.read();
      if (value != -1) {
        return value;
      }
      firstEnds = true;
    }
    value = secondInputStream.read();

    if (firstOutputStream != null && value != -1) {
      firstOutputStream.write(value);
    }

    return value;
  }

  @Override
  public int read(@NonNull byte[] buffer, int byteOffset, int byteCount) throws IOException {
    checkOffsetAndCount(buffer.length, byteOffset, byteCount);

    int bytesRead;
    if (!firstEnds) {
      bytesRead = firstInputStream.read(buffer, byteOffset, byteCount);
      if (bytesRead != -1) {
        firstInputStreamTotalRead += bytesRead;
        FLog.w(TAG, "Read from firstInputStream " + bytesRead + " bytes. Totally read from source: " + firstInputStreamTotalRead + " bytes");

        return bytesRead;
      }
      FLog.w(TAG, "FirstInputStream ended. Totally read " + firstInputStreamTotalRead);
      firstEnds = true;
    }

    bytesRead = secondInputStream.read(buffer, byteOffset, byteCount);
    if (bytesRead != -1) {
      secondInputStreamTotalRead += bytesRead;
      FLog.w(TAG, "Read from secondInputStream " + bytesRead + " bytes. Totally read from source: " + secondInputStreamTotalRead + " bytes");
    }

    if (firstOutputStream != null && bytesRead != -1) {
      firstOutputStream.write(buffer, byteOffset, bytesRead);
      firstOutputStream.flush();
    }

    return bytesRead;
  }

  private void checkOffsetAndCount(int arrayLength, int offset, int count) {
    if ((offset | count) < 0 || offset > arrayLength || arrayLength - offset < count) {
      throw new ArrayIndexOutOfBoundsException();
    }
  }
}
