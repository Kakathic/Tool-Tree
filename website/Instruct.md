# [Tool-Tree](https://Kakathic.github.io/Tool-Tree)

**Add-on file format**

- Is a compressed file in .zip or .7z format

- After compression is complete, rename the extension to file.add

**Internal structure of add-on file**

```
file.add
└── (in file.add)
    ├── download.prop        # link to download add-on
    ├── addon.prop           # add-on information
    ├── icon.png (200x200)   # is the icon of the add-on
    ├── menu.sh              # 3-dot menu button
    ├── index.sh|index.xml   # After entering the page, all content will be displayed.
    ├── early_start.sh       # The first time the application starts, it will run the shell.
    ├── install.sh           # When the add-on is unzipped, it will run the shell.
    └── uninstall.sh         # remove add-on it will run shell
```

**Contents of addon.prop file**

```
# is a shell script file
id=test
name=Test add-on
author=Kakathic
description=Short description
version=1.0
versionCode=100
# if set to "true" root is required for add-on to work
root=false
```

**Add-on icon**

- The icon.png file is an image file that can be 100x100 ~ 200x200 in size

- Can also rename icon_true.png, icon_false.png, true is dark mode, false is light mode.

**Content inside index.bash, index.toml**

- There are many things that are difficult to say that can only be found out by yourself.

- [See details](https://Kakathic.github.io/Tool-Tree/website/Toml.html)
