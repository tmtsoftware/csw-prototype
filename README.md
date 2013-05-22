TMT Common Software (CSW)
=========================

Common Software is the package of services and infrastructure software that integrates the TMT software systems.

http://www.tmt.org


Build Instructions
------------------

A hierarchical build is defined (see project/Csw.scala, which is the root of the build).
To build, run 'sbt' in the top level directory and type one of the following commands:

<table>
    <tr>
        <td>compile</td>
        <td>compiles the sources</td>
    </tr>
    <tr>
        <td>gen-idea</td>
        <td>generates the Idea project and modules</td>
    </tr>
    <tr>
        <td>osgi-bundle</td>
        <td>compiles and builds the osgi bundles</td>
    </tr>
    <tr>
        <td>publish-local</td>
        <td>copies the bundles to the local ivy repository (~/.ivy2) so they can be referenced by other bundles</td>
    </tr>
    <tr>
        <td>test</td>
        <td>run the tests</td>
    </tr>
    <tr>
        <td>dist</td>
        <td>create the Akka microkernel standalone apps (see test/app for the source, target/testApp for the installation)</td>
    </tr>
</table>

Commands apply to the entire build unless otherwise specified.
You can narrow the focus to a subproject with the sbt "project" command.
For example: "project cs" sets the current project to "cs". The top level project is "csw".
