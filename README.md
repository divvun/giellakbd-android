# giellakbd-android

A fork of LatinIME for minority languages.

[![Build Status](https://travis-ci.org/divvun/giellakbd-android.svg?branch=master)](https://travis-ci.org/divvun/giellakbd-android)

## Building

**It is highly recommended to use Divvun's kbdgen tool to generate any keyboards.**

You will need to add an `app/gradle.local` file with the following template:

```
ext.app = [
    storeFile: "./some.keystore",
    keyAlias: "some key alias",
    packageName: "com.example",
    versionCode: 1,
    versionName: "0.0.0"
]
```