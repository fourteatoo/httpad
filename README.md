# HTTPAD

A simple macropad inspired by the many hardware implementations out
there.

Macropads are those nifty little kaypads, you plug into your computer
and use to simplify and speed-up the interaction with your
applications or desktop.  They offer a better tactile experience than
an on-screen widget -- real mechanical switches and knobs.  They are
quite flexibile -- you can assign any action or macro (hence the name)
to keys or knobs.  But, unless you want to invest in a top of the
range contraption, that would set you back hundreds of quid and
requires proprietary software to control and configure, they won't be
that practical or intuitive.

The hardware implementations out there don't offer much in terms of
visual feedback.  Usually, the user needs to write on the key caps
what they do, once she has finished their painful configuration
(QMK/Via), that is.  As for visual feedback, the most she can enjoy is
some dumb colour show, assuming the macropad offers RGB backlighting.

Sure, Emacs users may not see the point in such investement (Emacs =
Editor MACroS), but Gimp, Lightroom or most DAW users know the pain.

HTTPAD compromises on the tectile experience to offer a better visual
one.  Not to mention, cheaper.

This app strives to imitate such macropads on a mobile device you
already own.  Were you wondering what to do with that old mobile
phone, sitting around in a drawer?  Now you can turn it into a
macropad with HTTPAD.



## Installation

Download from https://github.com/fourteatoo/httpad

Compile

    $ lein uberjar
   
Copy the uberjar in your `~/bin` directory.


    $ cp target/httpad-<VERSION>-standalone.jar ~/bin/httpad.jar

## Configuration

Before you can do anything with it, HTTPAD needs a configuration file.
That would be `~/.htppad`.  In this file you configure which port you
want to use and what buttons to display.

## Usage

HTTPAD is meant to be run from your `.xprofile` at the login.  It
listens to the port you have configured for connections from any
Androi/iOS device you have dedicated to the purpose.

    $ java -jar httpad.jar

From your mobile device connect to http://yourcomputer:port/.  You
should see a keypad as you configured it yourself.

## Options

None at the moment.  See the configuration file instead.

### Bugs

To be expected.

## License

Copyright © 2026 Walter C. Pelissero <walter@pelissero.de>

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
