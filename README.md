# giellakbd-android


A fork of LatinIME (by Google for Android), targeting marginalised languages that also deserve first-class status on mobile operating systems.

Note. This project is the place where all the code is kept. It also has som basic configuration, but crucially - you can't open this project in Android Studio and run it (yes, you can. In a way, but it's no good. Look. Stop interrupting me and pay attention).

## Backgound

On one hand, you have all the linguists and other related folks at UiT working on getting spellers and suggestions and reasonable keyboard layouts. They work in one repo per language.

On the other hand you have the android app that's supposed to be one single app that ships many languages. 

The linguists don't only make the android app, they make ios, mac, windows etc. They shouldn't have to worry about android stores etc. To facilitate this, they create a yaml layout such as [this](https://github.com/giellalt/keyboard-sms/blob/main/sms.kbdgen/layouts/sms.yaml)

If you wanna build an android app though, that won't help you. You need layout xml:s. Introducing - [kbdgen](https://github.com/divvun/kbdgen). Kbdgen downloads layout files. It does it by cloning this repo and then meta-programming on some layouts and xmls. 

To compile the problem slightly, we don't really want to distribute one keyboard per language. Hence, we combine multiple languages per keyboard. There are two repos for this. https://github.com/divvun/divvun-dev-keyboard and https://github.com/divvun/divvun-keyboard . Right? Right. 


## Building

You're gonna need [kbdgen](https://github.com/divvun/kbdgen). You can download it from pahkat. Technically.

```
$ wget https://pahkat.uit.no/devtools/download/kbdgen\?channel\=nightly\&platform\=macos
```


The following will download divvun-dev-keyboard and create you a runnable android project. 

```
cd source/divvun
git clone git@github.com:divvun/kbdgen.git
git clone git@github.com:divvun/divvun-dev-keyboard.git 
cd kbdgen
cargo run -- fetch -b /Users/srdkvr/source/divvun/divvun-dev-keyboard/divvun-dev.kbdgen #fetches deps since divvun-dev-keyboard is shallow
cargo run -- target --bundle-path /Users/srdkvr/source/divvun/divvun-dev-keyboard/divvun-dev.kbdgen --output-path ~/source/divvun/android_keyboard android build
```

You might need
```bash
$ brew install imagemagick@6
$ echo 'export PATH="/opt/homebrew/opt/imagemagick@6/bin:$PATH"' >> ~/.zshrc
```

If you got this right, you can open `~/source/divvun/android_keyboard` in Android Studio and work on your project. If you make any code-changes, make sure to upload them in this repo and this repo alone. Don't upload all the files kbdgen put in this directory for you. 


## License

Apache 2 license. See LICENSE.
