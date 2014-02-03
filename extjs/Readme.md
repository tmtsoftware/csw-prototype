ExtJS Web UI Setup
==================

This directory contains an ExtJS workspace. It was originally generated with the command:

    sencha generate workspace /path/to/workspace

Since the result is checked in, you don't need to run this command. Later we may need to
run "sencha upgrade" to upgrade to a newer version.

You need to install the Sencha Cmd tools first, available from:
http://www.sencha.com/products/sencha-cmd/download.

The workspace contains all of the ExtJS based apps used here, so that they can share code and be
compiled (minified). Currently, the only web app is "assembly1", which is used by test/pkg/container1
to display a form.

The top level index.hml file in this directory provides links to the development and production versions.
The development version runs directly off the JavaScript sources (but still requires a CSS file from the
compiled version).

Compiling the JavaScript
------------------------

The production version is the result of running the command:

   sencha app build

in the the application directory. For example.

    cd assembly1
    sencha app build

This generates an app.js file under build/production that is minified.
Since compiling is slow, it is easier to use the sources directly during development.
However the application bootstrap.css file links to a compiled CSS file, so you need to run the
build (sencha app build) at least once. By default this command compiles the "production" version.
To compile a "testing" version, use the command:

    sencha app build testing


Configuring the location of the ExtJS Applications
--------------------------------------------------

In the Scala code, the static Spray route in CommandServiceHttpRoute needs to know where to find the
JavaScript resources. This is defined in a config value "csw.extjs.root", which can normally be defined
in the application's reference.conf. A default value is set in cmd/src/main/resources/reference.con.
Currently the main sbt Build.scala file adds a -Dcsw.extjs.root=... option for the container1 demo project,
automatically inserting the correct path.
