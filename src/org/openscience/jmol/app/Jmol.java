/* $RCSfile$
 * $Author$
 * $Date$
 * $Revision$
 *
 * Copyright (C) 2000-2005  The Jmol Development Team
 *
 * Contact: jmol-developers@lists.sf.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.openscience.jmol.app;

import java.awt.*;
import java.util.Hashtable;
import java.util.Map;

import javax.swing.JFrame;

import org.openscience.jmol.app.jmolpanel.JmolPanel;
import org.openscience.jmol.app.jmolpanel.Splash;

public class Jmol extends JmolPanel {

  public Jmol(JmolApp jmolApp, Splash splash, JFrame frame, Jmol parent, int startupWidth,
      int startupHeight, Map<String, Object> vwrOptions, Point loc) {
    super(jmolApp, splash, frame, parent, startupWidth, startupHeight, vwrOptions, loc);

    //Assumes oculus is in extended desktop mode and is designated as the second screen (i.e index 1)
    if(vwrOptions.get("oculusVRMode") != null){
      showOnScreen(1, frame);
	}
  }

  public static void showOnScreen( int screen, JFrame frame ) {
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice[] gs = ge.getScreenDevices();
    if( screen > -1 && screen < gs.length ) {
      gs[screen].setFullScreenWindow( frame );
    } else if( gs.length > 0 ) {
      gs[0].setFullScreenWindow( frame );
    } else {
      throw new RuntimeException( "No Screens Found" );
    }
  }

  public static void main(String[] args) {
    JmolApp jmolApp = new JmolApp(args);
    startJmol(jmolApp);
  }

  public static Jmol getJmol(JFrame baseframe, 
                             int width, int height, Map<String, Object> vwrOptions) {
    JmolApp jmolApp = new JmolApp(new String[] {});
    jmolApp.startupHeight = height;
    jmolApp.startupWidth = width;
    jmolApp.info = (vwrOptions == null ? new Hashtable<String, Object>() : vwrOptions);
    return getJmol(jmolApp, baseframe);
  }
  
}
