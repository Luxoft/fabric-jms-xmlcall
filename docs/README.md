to build docs install

```
 $ sudo -H pip install Sphinx rst2pdf
```

then call
```
 $ make <target>
```

<target> is one of:

* `html`: generate html
* `pdf`: generate simple pdf
* `latexpdf`: generate pdf via latex.
    Post на хабре про это. https://habrahabr.ru/post/328182/
    Не юзается, но может быть полезным.
* `help`: show list of targets
* `linkcheck`: validate links. Doesn't work in luxoft environment

