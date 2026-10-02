# [Tool-Tree](https://Kakathic.github.io/Tool-Tree)

**Add-on file format**

- Is a compressed file in .zip or .7z format

- After compression is complete, rename the extension to file.add

**Internal structure of add-on file**

```
file.add
└── (in file.add)
    ├── download.bash        # link to download add-on
    ├── Add-on.bash           # add-on information
    ├── icon.png (200x200)   # is the icon of the add-on
    ├── index.bash|index.toml   # After entering the page, all content will be displayed.
    ├── early_start.bash       # The first time the application starts, it will run the shell.
    ├── install.bash           # When the add-on is unzipped, it will run the shell.
    └── uninstall.bash         # remove add-on it will run shell
```

**Contents of Add-on.bash file**

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
