package cn.reactnative.modules.update;

import android.os.AsyncTask;
import android.util.Log;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

public class DownloadTask extends AsyncTask<DownloadTaskParams, Void, Void> {

    private void removeDirectory(File file) throws IOException {
        if (UpdateContext.DEBUG) {
            Log.d("RNUpdate", "Removing " + file);
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
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url)
                .build();
        Response response = client.newCall(request).execute();
        if (response.code() > 299) {
            throw new Error("Server return code " + response.code());
        }
        ResponseBody body = response.body();
        long contentLength = body.contentLength();
        BufferedSource source = body.source();

        if (writePath.exists()) {
            writePath.delete();
        }

        BufferedSink sink = Okio.buffer(Okio.sink(writePath));

        if (UpdateContext.DEBUG) {
            Log.d("RNUpdate", "Downloading " + url);
        }

        long bytesRead;
        long totalRead = 0;
        int DOWNLOAD_CHUNK_SIZE = 4096;
        while ((bytesRead = source.read(sink.buffer(), DOWNLOAD_CHUNK_SIZE)) != -1) {
            totalRead += bytesRead;
            if (UpdateContext.DEBUG) {
                Log.d("RNUpdate", "Progress " + totalRead + "/" + contentLength);
            }
        }
        if (totalRead != contentLength) {
            throw new Error("Unexpected eof while reading ppk");
        }
        sink.writeAll(source);
        sink.close();

        if (UpdateContext.DEBUG) {
            Log.d("RNUpdate", "Download finished");
        }
    }

    private byte[] buffer = new byte[1024];

    private void unzipToFile(ZipInputStream zis, File fmd) throws IOException {
        int count;

        FileOutputStream fout = new FileOutputStream(fmd);

        while ((count = zis.read(buffer)) != -1)
        {
            fout.write(buffer, 0, count);
        }

        fout.close();
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

            if (UpdateContext.DEBUG) {
                Log.d("RNUpdate", "Unzipping " + fn);
            }

            if (ze.isDirectory()) {
                fmd.mkdirs();
                continue;
            }

            unzipToFile(zis, fmd);
        }

        zis.close();

        if (UpdateContext.DEBUG) {
            Log.d("RNUpdate", "Unzip finished");
        }
    }

    private void doCleanUp(DownloadTaskParams param) throws IOException {
        if (UpdateContext.DEBUG) {
            Log.d("RNUpdate", "Start cleaning up");
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
            if (UpdateContext.DEBUG) {
                e.printStackTrace();
            }
            params[0].listener.onDownloadFailed(e);
        }
        return null;
    }

}
