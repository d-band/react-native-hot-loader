package com.dband.rn.modules;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

class DownloadTask extends AsyncTask<DownloadTaskParams, Void, Void> {

    private static final int BUFFER_SIZE = 1024 * 256;

    private void removeDirectory(File file) throws IOException {
        if (RCTHotLoaderContext.DEBUG) {
            Log.d("RCTHotLoaderModule", "Removing " + file);
        }
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (File f : files) {
                String name = f.getName();
                if (name.equals(".") || name.equals("..")) {
                    continue;
                }
                removeDirectory(f);
            }
        }
        if (file.exists() && !file.delete()) {
            throw new IOException("Failed to delete directory");
        }
    }

    private void downloadFile(String url, File writePath) throws IOException {
        if (RCTHotLoaderContext.DEBUG) {
            Log.d("RCTHotLoaderModule", "Downloading " + url);
        }
        HttpURLConnection connection = null;
        BufferedInputStream bis = null;
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;

        try {
            URL downloadUrl = new URL(url);
            connection = (HttpURLConnection) (downloadUrl.openConnection());

            long totalBytes = connection.getContentLength();
            long receivedBytes = 0;

            bis = new BufferedInputStream(connection.getInputStream());

            if (writePath.exists()) {
                writePath.delete();
            }
            fos = new FileOutputStream(writePath);
            bos = new BufferedOutputStream(fos, BUFFER_SIZE);
            byte[] data = new byte[BUFFER_SIZE];
            int numBytesRead;
            while ((numBytesRead = bis.read(data, 0, BUFFER_SIZE)) >= 0) {
                receivedBytes += numBytesRead;
                bos.write(data, 0, numBytesRead);
                if (RCTHotLoaderContext.DEBUG) {
                    Log.d("RCTHotLoaderModule", "Progress " + receivedBytes + "/" + totalBytes);
                }
            }

            if (totalBytes != receivedBytes && totalBytes != -1) {
                throw new IOException("Unexpected eof while reading ppk");
            }
        } finally {
            if (bos != null) bos.close();
            if (fos != null) fos.close();
            if (bis != null) bis.close();
            if (connection != null) connection.disconnect();
        }

        if (RCTHotLoaderContext.DEBUG) {
            Log.d("RCTHotLoaderModule", "Download finished");
        }
    }

    private void unzipToFile(ZipInputStream zis, File fmd) throws IOException {
        int count;
        byte[] buffer = new byte[BUFFER_SIZE];
        FileOutputStream fos = new FileOutputStream(fmd);

        while ((count = zis.read(buffer)) != -1)
        {
            fos.write(buffer, 0, count);
        }

        fos.close();
        zis.closeEntry();
    }

    private void doDownload(DownloadTaskParams param) throws IOException {
        downloadFile(param.url, param.zipFilePath);

        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(param.zipFilePath)));
        ZipEntry ze;

        removeDirectory(param.unzipDirectory);
        param.unzipDirectory.mkdirs();

        while ((ze = zis.getNextEntry()) != null)
        {
            String fn = ze.getName();
            File fmd = new File(param.unzipDirectory, fn);

            if (RCTHotLoaderContext.DEBUG) {
                Log.d("RCTHotLoaderModule", "Unzipping " + fn);
            }

            if (ze.isDirectory()) {
                fmd.mkdirs();
                continue;
            }

            File parent = fmd.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            }

            unzipToFile(zis, fmd);
        }

        zis.close();

        if (RCTHotLoaderContext.DEBUG) {
            Log.d("RCTHotLoaderModule", "Unzip finished");
        }
    }

    private void doCleanUp(DownloadTaskParams param) throws IOException {
        if (RCTHotLoaderContext.DEBUG) {
            Log.d("RCTHotLoaderModule", "Start cleaning up");
        }
        File root = param.unzipDirectory;
        for (File sub : root.listFiles()) {
            if (sub.getName().charAt(0) == '.') {
                continue;
            }
            if (sub.isFile()) {
                sub.delete();
            } else {
                if (sub.getName().equals(param.hash) || sub.getName().equals(param.originHash)) {
                    continue;
                }
                removeDirectory(sub);
            }
        }
    }

    @Override
    protected Void doInBackground(DownloadTaskParams... params) {
        try {
            switch (params[0].type) {
                case DownloadTaskParams.TASK_TYPE_FULL_DOWNLOAD:
                    doDownload(params[0]);
                    break;
                case DownloadTaskParams.TASK_TYPE_CLEAR_UP:
                    doCleanUp(params[0]);
                    break;
            }
            params[0].listener.onDownloadCompleted();
        } catch (Throwable e) {
            if (RCTHotLoaderContext.DEBUG) {
                e.printStackTrace();
            }
            params[0].listener.onDownloadFailed(e);
        }
        return null;
    }

}
