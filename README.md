# Intellij Joined Tab Scrolling Plugin

Plugin to let several tabs of the same file scroll together, as if the editor wraps around. 

## Don't you wish your widescreen monitor could do wrap-around scrolling? Now it can!

![Gif Demo](./DemoVideoShort-intellij-joined-tab-scrolling.gif "Animated Gif")

Watch a longer [Demo Video](./DemoVideo.mov "Longer demo video").

<img src="/Screenshot_how_it_works.png" alt="How it works" style="width:500px"/>

When you split IntelliJ editor tabs: scroll one window and the others automatically keep in sync. 
Let multiple tabs scroll continuously together. Multiple side by side let you see more code in
context on a widescreen monitor.

Works well with <a href="https://github.com/Vektah/CodeGlance">CodeGlance</a> for a Sublime style 
preview pane in the right gutter. (That's what I'm using in the demos).

# How To Install

1. Open Intellij Preferences
1. Click Plugins
1. Click "Browse Repositories..."
1. Search for "Joined", click install. 
1. Restart IntelliJ.


# See Also

* See [META-INF/plugin.xml](./META-INF/plugin.xml) for some release notes
* See this plugin's official Intellij [plugin page](https://plugins.jetbrains.com/plugin/8028?pr=)
* File a bug with the [Issue Tracker](https://github.com/jawspeak/intellij-joined-tab-scrolling/issues)
* Me on twitter: [@jawspeak](https://twitter.com/jawspeak). Thanks [@mikedussault](https://twitter.com/mikedussault) for the idea.


# Developer notes

(if you're hacking on editor plugins too)

Several places to read more about these API's used to manipulate Editors / tabs:

* https://github.com/Vektah/CodeGlance/tree/master/src/main/java/net/vektah/codeglance
* https://github.com/mustah/TabSwitch/blob/master/META-INF/plugin.xml
* https://github.com/siosio/FileOpenPlugin/blob/master/src/siosio/fileopen/OpenRightTabAction.kt
* https://github.com/mjedynak/File-Name-Grabber
* https://github.com/alp82/idea-tabsession/blob/master/src/com/squiek/idea/plugin/tabsession/SessionComponent.java

