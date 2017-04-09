package cn.reactnative.modules.update;

import java.io.File;


public class DownloadTaskParams {
    static final int TASK_TYPE_FULL_DOWNLOAD = 1;
    static final int TASK_TYPE_CLEAR_UP = 0; //Keep hash & originHash

    int         type;
    String      url;
    String      hash;
    String      originHash;
    File        zipFilePath;
    File        unzipDirectory;
    UpdateContext.DownloadFileListener listener;
}
