## CkDownloader Android

An Android library for downloading files, particularly videos or audio, using the download segment chunks method while passing header range bytes. Typically utilized for servers that support header range bytes, bypass throttle web server and partial stream 206

<img src="ss/ss1.png" width="240" height="480" />

This is network tracking behind downloading files

<img src="ss/ss2.png" height="320" /> <img src="ss/ss3.png" height="320" />

### Download
``` groovy
 implementation ""
```

### Usage
You can follow sample project on <a href="app/src/main">this</a>

### Notes
- Host server must supported header content length ranges bytes, if not the downloaded file will be corrupted
- Not support pause/resume for now, just delete and re-download
- Feel free for pull request :)