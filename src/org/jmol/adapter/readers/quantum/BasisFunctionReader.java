/* $RCSfile$
 * $Author: egonw $
 * $Date: 2006-03-18 15:59:33 -0600 (Sat, 18 Mar 2006) $
 * $Revision: 4652 $
 *
 * Copyright (C) 2003-2005  Miguel, Jmol Development, www.jmol.org
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
package org.jmol.adapter.readers.quantum;

import org.jmol.adapter.smarter.AtomSetCollectionReader;
import org.jmol.quantum.QS;
import org.jmol.util.Logger;

import javajs.util.Lst;
import javajs.util.PT;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Hashtable;

import java.util.Map;


/**
 * 
 * @author hansonr <hansonr@stolaf.edu>
 */
abstract class BasisFunctionReader extends AtomSetCollectionReader {

  public Lst<int[]> shells;

  protected Map<String, Object> moData = new Hashtable<String, Object>();
  protected Lst<Map<String, Object>> orbitals = new  Lst<Map<String, Object>>();
  protected int nOrbitals = 0;
  protected boolean ignoreMOs = false;
  protected String alphaBeta = "";

  protected int[][] dfCoefMaps;
  
  private String[] filterTokens;
  private boolean filterIsNot;

  private String spin; 

  /**
   * check line for filter options
   * 
   * @return true if a match
   */
  protected boolean filterMO() {
    boolean isHeader = (line.indexOf('\n') == 0);
    if (!isHeader && !doReadMolecularOrbitals)
      return false;
    boolean isOK = true;
    line += " " + alphaBeta;
    String ucline = line.toUpperCase();
    if (filter != null) {
      int nOK = 0;
      if (filterTokens == null) {
        filterIsNot = (filter.indexOf("!") >= 0);
        filterTokens = PT.getTokens(filter.replace('!', ' ').replace(',', ' ')
            .replace(';', ' '));
      }
      for (int i = 0; i < filterTokens.length; i++)
        if (ucline.indexOf(filterTokens[i]) >= 0) {
          if (!filterIsNot) {
            nOK = filterTokens.length;
            break;
          }
        } else if (filterIsNot) {
          nOK++;
        }
      isOK = (nOK == filterTokens.length);
      if (!isHeader)
        Logger.info("filter MOs: " + isOK + " for \"" + line + "\"");
    }
    spin = (ucline.indexOf("ALPHA") >= 0 ? "alpha"
        : ucline.indexOf("BETA") >= 0 ? "beta" : null);
    return isOK;
  }

  protected void setMO(Map<String, Object> mo) {
    if (dfCoefMaps != null)
      mo.put("dfCoefMaps", dfCoefMaps);
    orbitals.addLast(mo);
    mo.put("index", Integer.valueOf(orbitals.size()));
    if (spin != null)
      mo.put("spin", spin);
  }
  
  public class MOEnergySorter implements Comparator<Object>{
    @Override
    @SuppressWarnings("unchecked")
    public int compare(Object a, Object b) {
      float ea = ((Float) ((Map<String, Object>)a).get("energy")).floatValue();
      float eb = ((Float) ((Map<String, Object>)b).get("energy")).floatValue();
      return (ea < eb ? -1 : ea > eb ? 1 : 0);
    }
  }

 
  /**
   * 
   * finds the position in the Jmol-required list of function types. This list is
   * reader-dependent. 
   * 
   * @param fileList 
   * @param shellType 
   * @param jmolList 
   * @param minLength 
   * @return            true if successful
   * 
   */
  protected boolean getDFMap(String fileList, int shellType, String jmolList, int minLength) {
   if (fileList.equals(jmolList))
      return true;
   getDfCoefMaps();
   boolean isOK = QS.createDFMap(dfCoefMaps[shellType], fileList, jmolList, minLength);
    if (!isOK)
      Logger.error("Disabling orbitals of type " + shellType + " -- Cannot read orbital order for: " + fileList + "\n expecting: " + jmolList);
    return isOK;
  }

  protected int nCoef;

  public int[][] getDfCoefMaps() {
    return (dfCoefMaps == null ? (dfCoefMaps = QS.getNewDfCoefMap()) : dfCoefMaps);
  }

  final protected static String canonicalizeQuantumSubshellTag(String tag) {
    char firstChar = tag.charAt(0);
    if (firstChar == 'X' || firstChar == 'Y' || firstChar == 'Z') {
      char[] sorted = tag.toCharArray();
      Arrays.sort(sorted);
      return new String(sorted);
    } 
    return tag;
  }
  
  protected int fixSlaterTypes(int typeOld, int typeNew) {
    // in certain cases we assume Cartesian and then later have to 
    // correct that. 
    if (shells == null)
      return 0;
    nCoef = 0;
    //System.out.println("----");
    for (int i = shells.size(); --i >=0 ;) {
      int[] slater = shells.get(i);
      if (slater[1] == typeOld)
        slater[1] = typeNew;
      int m = getDfCoefMaps()[slater[1]].length;
      //System.out.println("i=" + i + " nCoef=" + nCoef + " t=" + slater[1] + " m=" + m);
      nCoef += m;
    }
    return nCoef;
  }

  public static int getQuantumShellTagIDSpherical(String tag) {
    return QS.getQuantumShellTagIDSpherical(tag);
  }

  public static int getQuantumShellTagID(String tag) {
    return QS.getQuantumShellTagID(tag);
  }

  public static String getQuantumShellTag(int id) {
    return QS.getQuantumShellTag(id);
  }

  @Override
  protected void discardPreviousAtoms() {
    asc.discardPreviousAtoms();
    moData.remove("mos");
    orbitals.clear();
  }

}
