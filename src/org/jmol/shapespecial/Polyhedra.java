/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2007-03-29 04:39:40 -0500 (Thu, 29 Mar 2007) $
 * $Revision: 7248 $
 *
 * Copyright (C) 2004-2005  The Jmol Development Team
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

package org.jmol.shapespecial;

import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;

import javajs.util.AU;
import javajs.util.Lst;
import javajs.util.M4;
import javajs.util.Measure;
import javajs.util.P3;
import javajs.util.P4;
import javajs.util.PT;
import javajs.util.SB;
import javajs.util.V3;

import org.jmol.api.AtomIndexIterator;
import org.jmol.api.SmilesMatcherInterface;
import org.jmol.api.SymmetryInterface;
import org.jmol.c.PAL;
import org.jmol.java.BS;
import org.jmol.modelset.Atom;
import org.jmol.modelset.Bond;
import org.jmol.script.SV;
import org.jmol.script.T;
import org.jmol.shape.AtomShape;
import org.jmol.util.BSUtil;
import org.jmol.util.C;
import org.jmol.util.Logger;
import org.jmol.util.Normix;

public class Polyhedra extends AtomShape {

//  private final static float DEFAULT_DISTANCE_FACTOR = 1.85f;
  private final static float DEFAULT_FACECENTEROFFSET = 0.25f;
  private final static int EDGES_NONE = 0;
  public final static int EDGES_ALL = 1;
  public final static int EDGES_FRONT = 2;
  private final static int MAX_VERTICES = 250;
  private final static int FACE_COUNT_MAX = MAX_VERTICES - 3;
  private final static int MAX_OTHER = MAX_VERTICES + FACE_COUNT_MAX + 1;
  private P3[] otherAtoms = new P3[MAX_OTHER];
  private V3[] normalsT = new V3[MAX_VERTICES + 1];
  private int[][] planesT = AU.newInt2(MAX_VERTICES);
  private final static P3 randomPoint = P3.new3(3141f, 2718f, 1414f);

  private static final int MODE_BONDING = 1;
  private static final int MODE_POINTS = 2;
  private static final int MODE_RADIUS = 3;
  private static final int MODE_BITSET = 4;
  private static final int MODE_UNITCELL = 5;
  private static final int MODE_INFO = 6;
  /**
   * a dot product comparison term
   */
  private static final float DEFAULT_PLANAR_PARAM = 0.98f;
  private static final float CONVEX_HULL_MAX = 0.02f;

  public int polyhedronCount;
  public Polyhedron[] polyhedrons = new Polyhedron[32];
  public int drawEdges;

  private float radius, radiusMin, pointScale;
  private int nVertices;

  float faceCenterOffset;
//  float distanceFactor = Float.NaN;
  boolean isCollapsed;
  boolean isFull;

  private boolean iHaveCenterBitSet;
  private boolean bondedOnly;
  private boolean haveBitSetVertices;

  private BS centers;
  private String thisID;
  private P3 center;
  private BS bsVertices;
  private BS bsVertexCount;

  private boolean useUnitCell;
  private int nPoints;
  private float planarParam;
  private Map<String, SV> info;
  private float distanceRef;
  private int modelIndex;

  @SuppressWarnings("unchecked")
  @Override
  public void setProperty(String propertyName, Object value, BS bs) {
    if (thisID != null)
      bs = new BS();
    if ("init" == propertyName) {
      faceCenterOffset = DEFAULT_FACECENTEROFFSET;
      //distanceFactor = 
      planarParam = Float.NaN;
      radius = radiusMin = pointScale = 0.0f;
      nVertices = 0;
      nPoints = 0;
      modelIndex = -1;
      bsVertices = null;
      thisID = null;
      center = null;
      useUnitCell = false;
      centers = null;
      info = null;
      bsVertexCount = new BS();
      bondedOnly = isCollapsed = isFull = iHaveCenterBitSet = false;
      haveBitSetVertices = false;
      if (Boolean.TRUE == value)
        drawEdges = EDGES_NONE;
      return;
    }

    if ("generate" == propertyName) {
      if (!iHaveCenterBitSet) {
        centers = bs;
        iHaveCenterBitSet = true;
      }
      deletePolyhedra();
      buildPolyhedra();
      return;
    }

    // thisID, center, and model all involve polyhedron ID ...

    if ("thisID" == propertyName) {
      thisID = (String) value;
      return;
    }

    if ("center" == propertyName) {
      center = (P3) value;
      return;
    }

    if ("offset" == propertyName) {
      if (thisID != null)
        offsetPolyhedra((P3) value);
      return;
    }

    if ("scale" == propertyName) {
      if (thisID != null)
        scalePolyhedra(((Float) value).floatValue());
      return;
      
    }

    if ("model" == propertyName) {
      modelIndex = ((Integer) value).intValue();
      return;
    }

    if ("collapsed" == propertyName) {
      isCollapsed = true;
      return;
    }

    if ("full" == propertyName) {
      isFull = true;
      return;
    }

    if ("nVertices" == propertyName) {
      int n = ((Integer) value).intValue();
      if (n < 0) {
        if (-n >= nVertices) {
          bsVertexCount.setBits(nVertices, 1 - n);
          nVertices = -n;
        }
      } else {
        bsVertexCount.set(nVertices = n);
      }
      return;
    }

    if ("centers" == propertyName) {
      centers = (BS) value;
      iHaveCenterBitSet = true;
      return;
    }

    if ("unitCell" == propertyName) {
      useUnitCell = true;
      return;
    }

    if ("to" == propertyName) {
      bsVertices = (BS) value;
      return;
    }

    if ("toBitSet" == propertyName) {
      bsVertices = (BS) value;
      haveBitSetVertices = true;
      return;
    }

    if ("toVertices" == propertyName) {
      P3[] points = (P3[]) value;
      nPoints = Math.min(points.length, MAX_VERTICES);
      for (int i = nPoints; --i >= 0;)
        otherAtoms[i] = points[i];
      return;
    }

    if ("faceCenterOffset" == propertyName) {
      faceCenterOffset = ((Float) value).floatValue();
      return;
    }

    if ("distanceFactor" == propertyName) {
      // not a general user option
      // ignore 
      //distanceFactor = ((Float) value).floatValue();
      return;
    }

    if ("planarParam" == propertyName) {
      // not a general user option
      planarParam = ((Float) value).floatValue();
      return;
    }

    if ("bonds" == propertyName) {
      bondedOnly = true;
      return;
    }

    if ("info" == propertyName) {
      info = (Map<String, SV>) value;
      centers = (info.containsKey("center") ? null : BSUtil.newAndSetBit(info
          .get("atomIndex").intValue));
      iHaveCenterBitSet = true;
      return;
    }

    if ("delete" == propertyName) {
      if (!iHaveCenterBitSet)
        centers = bs;
      deletePolyhedra();
      return;
    }
    if ("on" == propertyName) {
      if (!iHaveCenterBitSet)
        centers = bs;
      setVisible(true);
      return;
    }
    if ("off" == propertyName) {
      if (!iHaveCenterBitSet)
        centers = bs;
      setVisible(false);
      return;
    }
    if ("noedges" == propertyName) {
      drawEdges = EDGES_NONE;
      return;
    }
    if ("edges" == propertyName) {
      drawEdges = EDGES_ALL;
      return;
    }
    if ("frontedges" == propertyName) {
      drawEdges = EDGES_FRONT;
      return;
    }
    if (propertyName.indexOf("color") == 0) {
      // from polyhedra command, we may not be using the prior select
      // but from Color we need to identify the centers.
      bs = ("colorThis" == propertyName && iHaveCenterBitSet ? centers
          : andBitSet(bs));
      boolean isPhase = ("colorPhase" == propertyName);
      Object cvalue = (isPhase ? ((Object[]) value)[1] : value);
      short colixEdge = (isPhase ? C.getColix(((Integer) ((Object[]) value)[0])
          .intValue()) : C.INHERIT_ALL);
      short colix = C.getColixO(isPhase ? cvalue : value);
      Polyhedron p;
      BS bs1 = findPolyBS(bs);
      for (int i = bs1.nextSetBit(0); i >= 0; i = bs1.nextSetBit(i + 1)) {
        p = polyhedrons[i];
        if (p.id == null) {
          p.colixEdge = colixEdge;
        } else {
          p.colixEdge = colixEdge;
          p.colix = colix;
        }
      }
      if (thisID != null)
        return;
      value = cvalue;
      propertyName = "color";
      //allow super
    }

    if (propertyName.indexOf("translucency") == 0) {
      // from polyhedra command, we may not be using the prior select
      // but from Color we need to identify the centers.
      boolean isTranslucent = (value.equals("translucent"));
      if (thisID != null) {
        BS bs1 = findPolyBS(bs);
        Polyhedron p;
        for (int i = bs1.nextSetBit(0); i >= 0; i = bs1.nextSetBit(i + 1)) {
          p = polyhedrons[i];
          p.colix = C.getColixTranslucent3(p.colix, isTranslucent,
              translucentLevel);
          if (p.colixEdge != 0)
            p.colixEdge = C.getColixTranslucent3(p.colixEdge, isTranslucent,
                translucentLevel);
        }
        return;
      }
      bs = ("translucentThis".equals(value) && iHaveCenterBitSet ? centers
          : andBitSet(bs));
      if (value.equals("translucentThis"))
        value = "translucent";
      //allow super
    }

    //    if ("token" == propertyName) {
    //      int tok = ((Integer) value).intValue();
    //      Swit
    //      if (tok == T.triangles && tok == T.notriangles) {
    //      } else {
    //        setLighting(tok == T.fullylit, bs);
    //      }
    //      return;
    //    }

    if ("radius" == propertyName) {
      radius = ((Float) value).floatValue();
      return;
    }

    if ("radius1" == propertyName) {
      radiusMin = radius;
      radius = ((Float) value).floatValue();
      return;
    }

    if ("points" == propertyName) {
      pointScale = ((Float) value).floatValue();
        pointsPolyhedra(bs, pointScale);
      return;
    }
    
    if (propertyName == "deleteModelAtoms") {
      int modelIndex = ((int[]) ((Object[]) value)[2])[0];
      for (int i = polyhedronCount; --i >= 0;) {
        Polyhedron p = polyhedrons[i];
        p.info = null;
        if (p.modelIndex > modelIndex) {
          p.modelIndex--;
        } else if (p.modelIndex == modelIndex) {
          polyhedronCount--;
          polyhedrons = (Polyhedron[]) AU.deleteElements(polyhedrons, i, 1);
        }
      }
      //pass on to AtomShape
    }

    setPropAS(propertyName, value, bs);
  }

  private void pointsPolyhedra(BS bs, float pointScale) {
    bs = findPolyBS(thisID == null ? bs : null);
    for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1))
      polyhedrons[i].pointScale = pointScale;  
  }

  private void scalePolyhedra(float scale) {
    BS bs = findPolyBS(null);
    for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1))
      polyhedrons[i].scale = scale;
  }

  private void offsetPolyhedra(P3 value) {
    BS bs = findPolyBS(null);
    for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1))
      polyhedrons[i].setOffset(P3.newP(value));
  }

  @Override
  public boolean getPropertyData(String property, Object[] data) {
    int iatom = (data[0] instanceof Integer ? ((Integer) data[0]).intValue()
        : Integer.MIN_VALUE);
    String id = (data[0] instanceof String ? (String) data[0] : null);
    Polyhedron p;
    if (property == "checkID") {
      return checkID(id);
    }
    if (property == "info") {
      // note that this does not set data[1] to null -- see ScriptExpr
      p = findPoly(id, iatom, true);
      if (p == null)
        return false;
      data[1] = p.getInfo(vwr, false);
      return true;
    }
    if (property == "points") {
      p = findPoly(id, iatom, false);
      if (p == null)
        return false;
      data[1] = p.vertices;
      return true;
    }
    if (property == "symmetry") {
      BS bsSelected = (BS) data[2];
      String s = "";
      for (int i = 0; i < polyhedronCount; i++) {
        p = polyhedrons[i];
        if (p.id == null ? 
            id != null || bsSelected != null && !bsSelected.get(p.centralAtom.i)
            : id == null || !PT.isLike(id, p.id))
          continue;
        s += (i + 1) + "\t" + p.getSymmetry(vwr, true) + "\n";
      }     
      data[1] = s;
      return true;
    }
    if (property == "move") {
      M4 mat = (M4) data[1];
      if (mat == null)
        return false;
      BS bsMoved = (BS) data[0];
      BS bs = findPolyBS(bsMoved);
      for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1))
        polyhedrons[i].move(mat, bsMoved);
      return true;
    }
    if (property == "getCenters") {
      // return matching BS in data[1]

      // number of vertices (>0) or faces (<0) is in data[0], now iatom
      String smiles = (String) data[1];
      BS bsSelected = (BS) data[2];
      SmilesMatcherInterface sm = (smiles == null ? null : vwr
          .getSmilesMatcher());
      if (sm != null)
        smiles = sm.cleanSmiles(smiles);
      int nv = (smiles != null ? PT.countChar(smiles, '*') : iatom);
      if (nv == 0)
        nv = Integer.MIN_VALUE;
      BS bs = new BS();
      if (smiles == null || sm != null)
        for (int i = polyhedronCount; --i >= 0;) {
          p = polyhedrons[i];
          if (p.id != null)
            continue; // for now, no support for non-atomic polyhedra
          // allows for -n being -(number of faces)
          if (nv != (nv > 0 ? p.nVertices
              : nv > Integer.MIN_VALUE ? -p.faces.length : nv))
            continue;
          iatom = p.centralAtom.i;
          if (bsSelected != null && !bsSelected.get(iatom))
            continue;
          if (smiles == null) {
            bs.set(iatom);
            continue;
          }
          p.getSymmetry(vwr, false);
          String smiles0 = p.polySmiles;
          try {
            if (sm.areEqual(smiles, smiles0) > 0)
              bs.set(iatom);
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      data[1] = bs;
      return true;
    }
    if (property == "allInfo") {
      Lst<Map<String, Object>> info = new Lst<Map<String, Object>>();
      for (int i = polyhedronCount; --i >= 0;)
        info.addLast(polyhedrons[i].getInfo(vwr, false));
      data[1] = info;
      return true;
    }
    return getPropShape(property, data);
  }

  private boolean checkID(String thisID) {
    this.thisID = thisID;
      return (findPolyBS(null).cardinality() > 0);
   }

  /**
   * 
   * @param id  may be null
   * @param iatom  may be < 0 to (along with id==null) to get matching polyhedron 
   * @param allowCollapsed
   * @return Polyhedron or null
   */
  private Polyhedron findPoly(String id, int iatom, boolean allowCollapsed) {
    for (int i = polyhedronCount; --i >= 0;) {
      Polyhedron p = polyhedrons[i]; 
      if (p.id == null ? p.centralAtom.i == iatom : p.id.equalsIgnoreCase(id))
        return (allowCollapsed || !polyhedrons[i].collapsed ? polyhedrons[i] : null);
    }
   return null;
  }

  private BS bsPolys = new BS();
  
  private BS findPolyBS(BS bsCenters) {
    BS bs = bsPolys;
    bs.clearAll();
    Polyhedron p;
    for (int i = polyhedronCount; --i >= 0;) {
      p = polyhedrons[i];
      if (p.id == null ? bsCenters != null && bsCenters.get(p.centralAtom.i) : isMatch(p.id))
        bs.set(i);
    }
    return bs;
  }

  private boolean isMatch(String id) {
    // could implement wildcards
    return thisID != null && PT.isMatch(id.toLowerCase(), thisID.toLowerCase(), true, true);
  }

  @Override
  public Lst<Map<String, Object>> getShapeDetail() {
    Lst<Map<String, Object>> lst = new Lst<Map<String, Object>>();
    for (int i = 0; i < polyhedronCount; i++)
      lst.addLast(polyhedrons[i].getInfo(vwr, false));
    return lst;
  }

  private BS andBitSet(BS bs) {
    BS bsCenters = new BS();
    for (int i = polyhedronCount; --i >= 0;) {
      Polyhedron p = polyhedrons[i];
      if (p.id == null)
        bsCenters.set(p.centralAtom.i);
    }
    bsCenters.and(bs);
    return bsCenters;
  }

  private void deletePolyhedra() {
    int newCount = 0;
    byte pid = PAL.pidOf(null);
    BS bs = findPolyBS(centers);
    for (int i = 0; i < polyhedronCount; ++i) {
      Polyhedron p = polyhedrons[i];
      if (bs.get(i)) {
        if (colixes != null && p.id == null)
          setColixAndPalette(C.INHERIT_ALL, pid, p.centralAtom.i);
        continue;
      }
      polyhedrons[newCount++] = p;
    }
    for (int i = newCount; i < polyhedronCount; ++i)
      polyhedrons[i] = null;
    polyhedronCount = newCount;
  }

  private void setVisible(boolean visible) {
    BS bs = findPolyBS(centers);
    for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
      Polyhedron p = polyhedrons[i];
      p.visible = visible;
      atoms[p.centralAtom.i].setShapeVisibility(vf, visible);
    }
  }

  private void buildPolyhedra() {
    Polyhedron p = null;
    if (thisID != null) {
      if (PT.isWild(thisID))
        return;
      if (center != null)
        p = validatePolyhedron(center, nPoints);
    } else if (info != null && info.containsKey("id")) {
      thisID = info.get("id").asString();
      p = new Polyhedron().setInfo(info, vwr.ms.at);
    }
    if (p != null) {
      addPolyhedron(p);
      return;
    }
    boolean useBondAlgorithm = radius == 0 || bondedOnly;
    int buildMode = (info != null ? MODE_INFO : nPoints > 0 ? MODE_POINTS
        : haveBitSetVertices ? MODE_BITSET : useUnitCell ? MODE_UNITCELL
            : useBondAlgorithm ? MODE_BONDING : MODE_RADIUS);
    AtomIndexIterator iter = (buildMode == MODE_RADIUS ? ms
        .getSelectedAtomIterator(null, false, false, false, false) : null);
    for (int i = centers.nextSetBit(0); i >= 0; i = centers.nextSetBit(i + 1)) {
      Atom atom = atoms[i];
      p = null;
      switch (buildMode) {
      case MODE_BITSET:
        p = constructBitSetPolyhedron(atom);
        break;
      case MODE_UNITCELL:
        p = constructUnitCellPolygon(atom, useBondAlgorithm);
        break;
      case MODE_BONDING:
        p = constructBondsPolyhedron(atom, 0);
        break;
      case MODE_RADIUS:
        vwr.setIteratorForAtom(iter, i, radius);
        p = constructRadiusPolyhedron(atom, iter);
        break;
      case MODE_INFO:
        p = new Polyhedron().setInfo(info, vwr.ms.at);
        break;
      case MODE_POINTS:
        p = validatePolyhedron(atom, nPoints);
        break;
      }
      if (p != null)
        addPolyhedron(p);
      if (haveBitSetVertices)
        break;
    }
    if (iter != null)
      iter.release();
  }

  private void addPolyhedron(Polyhedron p) {
    if (polyhedronCount == polyhedrons.length)
      polyhedrons = (Polyhedron[]) AU.doubleLength(polyhedrons);
    polyhedrons[polyhedronCount++] = p;
  }

  private Polyhedron constructBondsPolyhedron(Atom atom, int otherAtomCount) {
    distanceRef = 0;
    if (otherAtomCount == 0) {
      Bond[] bonds = atom.bonds;
      if (bonds == null)
        return null;
      float r2 = radius * radius;
      float r1 = radiusMin * radiusMin;
      float r;
      for (int i = bonds.length; --i >= 0;) {
        Bond bond = bonds[i];
        if (!bond.isCovalent())
          continue;
        Atom other = bond.getOtherAtom(atom);
        if (bsVertices != null && !bsVertices.get(other.i) || radius > 0
            && ((r = other.distanceSquared(atom)) > r2 || r < r1))
          continue;
        otherAtoms[otherAtomCount++] = other;
        if (otherAtomCount >= MAX_VERTICES)
          return null;
      }
    }
    return (otherAtomCount < 3 || nVertices > 0
        && !bsVertexCount.get(otherAtomCount) ? null : validatePolyhedron(atom,
        otherAtomCount));
  }

  private Polyhedron constructUnitCellPolygon(Atom atom,
                                              boolean useBondAlgorithm) {
    SymmetryInterface unitcell = vwr.ms.getUnitCellForAtom(atom.i);
    if (unitcell == null)
      return null;
    BS bsAtoms = BSUtil.copy(vwr.getModelUndeletedAtomsBitSet(atom.mi));
    if (bsVertices != null)
      bsAtoms.and(bsVertices);
    if (bsAtoms.isEmpty())
      return null;
    AtomIndexIterator iter = unitcell.getIterator(vwr, atom, atoms, bsAtoms,
        useBondAlgorithm ? 5f : radius);
    if (!useBondAlgorithm)
      return constructRadiusPolyhedron(atom, iter);
    float myBondingRadius = atom.getBondingRadius();
    if (myBondingRadius == 0)
      return null;
    float bondTolerance = vwr.getFloat(T.bondtolerance);
    float minBondDistance = (radiusMin == 0 ? vwr.getFloat(T.minbonddistance) : radiusMin);
    float minBondDistance2 = minBondDistance * minBondDistance;
    int otherAtomCount = 0;
    outer: while (iter.hasNext()) {
      Atom other = atoms[iter.next()];
      float otherRadius = other.getBondingRadius();
      P3 pt = iter.getPosition();
      float distance2 = atom.distanceSquared(pt);
      if (!vwr.ms.isBondable(myBondingRadius, otherRadius, distance2,
          minBondDistance2, bondTolerance))
        continue;
      for (int i = 0; i < otherAtomCount; i++)
        if (otherAtoms[i].distanceSquared(pt) < 0.01f)
          continue outer;
      otherAtoms[otherAtomCount++] = pt;
      if (otherAtomCount >= MAX_VERTICES)
        return null;
    }
    return constructBondsPolyhedron(atom, otherAtomCount);
  }

  private Polyhedron constructBitSetPolyhedron(Atom atom) {
    bsVertices.clear(atom.i);
    if (bsVertices.cardinality() >= MAX_VERTICES)
      return null;
    int otherAtomCount = 0;
    distanceRef = 0;
    for (int i = bsVertices.nextSetBit(0); i >= 0; i = bsVertices
        .nextSetBit(i + 1))
      otherAtoms[otherAtomCount++] = atoms[i];
    return validatePolyhedron(atom, otherAtomCount);
  }

  private Polyhedron constructRadiusPolyhedron(Atom atom, AtomIndexIterator iter) {
    int otherAtomCount = 0;
    distanceRef = radius;
    float r2 = radius * radius;
    float r2min = radiusMin * radiusMin;
    outer: while (iter.hasNext()) {
      Atom other = atoms[iter.next()];
      P3 pt = iter.getPosition();
      if (pt == null) {
        // this will happen with standard radius atom iterator
        pt = other;
        if (bsVertices != null && !bsVertices.get(other.i))
          continue;
      }
      float r = atom.distanceSquared(pt);
      if (other.altloc != atom.altloc && other.altloc != 0 && atom.altloc != 0
          || r > r2 || r < r2min)
        continue;
      if (otherAtomCount == MAX_VERTICES)
        break;
       for (int i = 0; i < otherAtomCount; i++)
        if (otherAtoms[i].distanceSquared(pt) < 0.01f)
          continue outer;

      otherAtoms[otherAtomCount++] = pt;
    }
    return (otherAtomCount < 3 || nVertices > 0
        && !bsVertexCount.get(otherAtomCount) ? null : validatePolyhedron(atom,
        otherAtomCount));
  }

  private Polyhedron validatePolyhedron(P3 atomOrPt, int vertexCount) {
    P3[] points = otherAtoms;
    boolean collapsed = isCollapsed;
    int triangleCount = 0;
    int nPoints = vertexCount + 1;
    int ni = vertexCount - 2;
    int nj = vertexCount - 1;
    float planarParam = (Float.isNaN(this.planarParam) ? DEFAULT_PLANAR_PARAM
        : this.planarParam);

    points[vertexCount] = atomOrPt;
    P3 ptAve = P3.newP(atomOrPt);
    for (int i = 0; i < vertexCount; i++)
      ptAve.add(points[i]);
    ptAve.scale(1f / (vertexCount + 1));
    /*  Start by defining a face to be when all three distances
     *  are < distanceFactor * (longest central) but if a vertex is missed, 
     *  then expand the range. The collapsed trick is to introduce 
     *  a "simple" atom near the center but not quite the center, 
     *  so that our planes on either side of the facet don't overlap. 
     *  We step out faceCenterOffset * normal from the center.
     *  
     *  Alan Hewat pointed out the issue of faces that CONTAIN the center --
     *  square planar, trigonal and square pyramids, see-saw. In these cases with no
     *  additional work, you get a brilliance effect when two faces are drawn over
     *  each other. The solution is to identify this sort of face and, if not collapsed,
     *  to cut them into smaller pieces and only draw them ONCE by producing a little
     *  catalog. This uses the Point3i().toString() method.
     *  
     *  For these special cases, then, we define a reference point just behind the plane
     *  
     *  Note that this is NOT AN OPTION for ID-named polyhedra (Jmol 14.5.0 10/31/2015)
     */

    P3 ptRef = P3.newP(ptAve);
    BS bsThroughCenter = new BS();
    if (thisID == null)
      for (int pt = 0, i = 0; i < ni; i++)
        for (int j = i + 1; j < nj; j++)
          for (int k = j + 1; k < vertexCount; k++, pt++)
            if (isPlanar(points[i], points[j], points[k], ptRef))
              bsThroughCenter.set(pt);
    // this next check for distance allows for bond AND distance constraints
    int[][] triangles = planesT;
    P4 pTemp = new P4();
    V3 nTemp = new V3();
    float offset = faceCenterOffset;
    int fmax = FACE_COUNT_MAX;
    int vmax = MAX_VERTICES;
    BS bsTemp = Normix.newVertexBitSet();
    V3[] normals = normalsT;
    Map<Integer, Object[]> htNormMap = new Hashtable<Integer, Object[]>();
    Map<String, Object> htEdgeMap = new Hashtable<String, Object>();
    BS bsCenterPlanes = new BS();
    Lst<int[]> lstRejected = (isFull ? new Lst<int[]>() : null);
    Object[] edgeTest = new Object[3];
    V3 vAC = this.vAC;
    for (int i = 0, pt = 0; i < ni; i++)
      for (int j = i + 1; j < nj; j++) {
        for (int k = j + 1; k < vertexCount; k++, pt++) {
          if (triangleCount >= fmax) {
            Logger.error("Polyhedron error: maximum face(" + fmax
                + ") -- reduce RADIUS");
            return null;
          }
          if (nPoints >= vmax) {
            Logger.error("Polyhedron error: maximum vertex count(" + vmax
                + ") -- reduce RADIUS");
            return null;
          }
          boolean isThroughCenter = bsThroughCenter.get(pt);
          P3 rpt = (isThroughCenter ? randomPoint : ptAve);
          V3 normal = new V3();
          boolean isWindingOK = Measure.getNormalFromCenter(rpt, points[i],
              points[j], points[k], !isThroughCenter, normal, vAC);
          // the standard face:
          int[] t = new int[] { isWindingOK ? i : j, isWindingOK ? j : i, k, -7 };
          float err = checkFacet(points, vertexCount, t, normal, pTemp, nTemp,
              vAC, htNormMap, htEdgeMap, planarParam, bsTemp, edgeTest);
          if (err != 0) {
            if (isFull && err != Float.MAX_VALUE && err < 0.5f) {
              t[3] = (int) (err * 100);
              lstRejected.addLast(t);
            }
            continue;
          }
          normals[triangleCount] = normal;
          triangles[triangleCount] = t;
          if (isThroughCenter) {
            bsCenterPlanes.set(triangleCount++);
          } else if (collapsed) {
            points[nPoints] = new P3();
            points[nPoints].scaleAdd2(offset, normal, atomOrPt);
            ptRef.setT(points[nPoints]);
            addFacet(i, j, k, ptRef, points, normals, triangles,
                triangleCount++, nPoints, isWindingOK, vAC);
            addFacet(k, i, j, ptRef, points, normals, triangles,
                triangleCount++, nPoints, isWindingOK, vAC);
            addFacet(j, k, i, ptRef, points, normals, triangles,
                triangleCount++, nPoints, isWindingOK, vAC);
            nPoints++;
          } else {
            triangleCount++;
          }
        }
      }
    nPoints--;
    if (Logger.debugging) {
      Logger.info("Polyhedron planeCount=" + triangleCount + " nPoints="
          + nPoints);
      for (int i = 0; i < triangleCount; i++)
        Logger.info("Polyhedron " + PT.toJSON("face[" + i + "]", triangles[i]));
    }
    //System.out.println(PT.toJSON(null, lstRejected));
    return new Polyhedron().set(thisID, modelIndex, atomOrPt, points, nPoints,
        vertexCount, triangles, triangleCount,
        getFaces(triangles, triangleCount, htNormMap), normals, bsCenterPlanes,
        collapsed, distanceRef, pointScale);
  }
  
  /**
   * Add one of the three "facets" that compose the planes of a "collapsed" polyhedron.
   * A mask of -2 ensures that only the [1-2] edge is marked as an outer edge.
   * 
   * @param i
   * @param j
   * @param k
   * @param ptRef slightly out from the center; based on centerOffset parameter
   * @param points
   * @param normals
   * @param faces
   * @param planeCount
   * @param nRef
   * @param isWindingOK
   * @param vTemp 
   */
  private void addFacet(int i, int j, int k, P3 ptRef, P3[] points,
                        V3[] normals, int[][] faces, int planeCount, int nRef,
                        boolean isWindingOK, V3 vTemp) {
    V3 normal = new V3();
    int ii = isWindingOK ? i : j;
    int jj = isWindingOK ? j : i;
    Measure.getNormalFromCenter(points[k], ptRef, points[ii], points[jj], false, normal, vTemp);
    normals[planeCount] = normal;
    faces[planeCount] = new int[] { nRef, ii, jj, -2 };
    //        System.out.println("draw ID \"d" + i+ j+ k + "\" VECTOR "
      //           + ptRef + " " + normal + " color blue \">" + i + j +k + isWindingOK
        //        + "\"");
  }

  /**
   * Clean out overlapping triangles based on normals and cross products. For
   * now, we use normixes, which are approximations of normals. It is not 100%
   * guaranteed that this will work.
   * 
   * @param points
   * @param nPoints
   * @param p1
   * @param norm
   * @param pTemp
   * @param vNorm
   * @param vAC
   * @param htNormMap
   * @param htEdgeMap
   * @param planarParam
   * @param bsTemp
   * @param edgeTest 
   * @return 0 if no error or value indicating the error
   */
  private float checkFacet(P3[] points, int nPoints, int[] p1, V3 norm,
                           P4 pTemp, V3 vNorm, V3 vAC,
                           Map<Integer, Object[]> htNormMap,
                           Map<String, Object> htEdgeMap, float planarParam,
                           BS bsTemp, Object[] edgeTest) {

    // Check here for a 3D convex hull:

    int i0 = p1[0];
    pTemp = Measure.getPlaneThroughPoints(points[i0], points[p1[1]],
        points[p1[2]], vNorm, vAC, pTemp);
    // See if all vertices are OUTSIDE the plane we are considering.
    //System.out.println(PT.toJSON(null, p1));
    P3 pt = points[i0];
    for (int j = 0; j < nPoints; j++) {
      if (j == i0)
        continue;
      //System.out.println("isosurface plane " + pTemp);
      //System.out.println("polyh distance to pt" + Measure.distanceToPlane(pTemp, points[j]));
      vAC.sub2(points[j], pt);
      vAC.normalize();
      float v = vAC.dot(vNorm);
      // this should be 0 for a perfect plane

      // we cannot just take a negative dot product as indication of being
      // inside the convex hull. That would be fine if this were about regular
      // polyhedra. But we can have imperfect quadrilateral faces that are slightly
      // bowed inward. 
      if (v > CONVEX_HULL_MAX) {
        return v;
      }
    }
    Integer normix = Integer.valueOf(Normix.getNormixV(norm, bsTemp));
    Object[] o = htNormMap.get(normix);
    //System.out.println(PT.toJSON(null, p1) + " " + normix);
    if (o == null) {
      // We must see if there is a close normix to this.
      // The Jmol lighting model uses 642 normals,
      // which have a neighboring vertex dot product of 0.990439.
      // This is too tight for polyhedron planarity. We relax this
      // requirement to 0.98 by default, but this can be relaxed even
      // more if desired.

      V3[] norms = Normix.getVertexVectors();
      for (Entry<Integer, Object[]> e : htNormMap.entrySet()) {
        Integer n = e.getKey();
        //System.out.println("norm " + n + " " + norms[n.intValue()].dot(norm) + " " + planarParam);
        if (norms[n.intValue()].dot(norm) > planarParam) {
          o = e.getValue();
          htNormMap.put(normix, o);
          normix = n;
          break;
        }
      }
//      if (normix == 563 || normix == 564)
//        System.out.println(normix);
      if (o == null)
        htNormMap.put(normix, o = new Object[] { new Lst<int[]>() });
    }

    //    System.out.println("testing poly" + PT.toJSON(null, p1) + normix);
//    if (p1[0] + p1[1] + p1[2] == 19+20+25) {
//      if (p1[0] == 19 && p1[1] == 20 &&  p1[2] == 25)
//      i0 = p1[0];
//    }
//      if (p1[0] + p1[1] + p1[2] == 19+25+27) {
//        if (p1[0] == 25 && p1[1] == 19 &&  p1[2] == 27)
//        i0 = p1[0];
//      }
//   
//
    //System.out.println(PT.toJSON(null, p1) + " " + normix);
    @SuppressWarnings("unchecked")
    Lst<int[]> faceEdgeList = (Lst<int[]>) o[0];
    for (int i = 0; i < 3; i++)
      if ((edgeTest[i] = addEdge(faceEdgeList, htEdgeMap, normix, p1, i, points)) == null)
        return Float.MAX_VALUE;
    for (int i = 0; i < 3; i++) {
      Object oo = edgeTest[i];
      if (oo == Boolean.TRUE)
        continue;
      Object[] oe = (Object[]) oo;
      faceEdgeList.addLast((int[]) oe[2]);
      htEdgeMap.put((String) oe[3], oe);
    }
    return 0;
  }
  
  /**
   * Check each edge to see that
   * 
   * (a) it has not been used before
   * 
   * (b) it does not have vertex points on both sides of it
   * 
   * (c) if it runs opposite another edge, then both edge masks are set properly
   * 
   * @param faceEdgeList
   * @param htEdgeMap
   * @param normix
   * @param p1
   * @param i
   * @param points
   * @return true if this triangle is OK
   * 
   */
  private Object addEdge(Lst<int[]> faceEdgeList,
                         Map<String, Object> htEdgeMap, Integer normix,
                         int[] p1, int i, P3[] points) {
    // forward maps are out
    int pt = p1[i];
    int pt1 = p1[(i + 1) % 3];
    String s1 = "_" + pt1;
    String s = "_" + pt;
    String edge = normix + s + s1;
    if (htEdgeMap.containsKey(edge))
      return null;
    //reverse maps are in
    String edge0 = normix + s1 + s;
    Object o = htEdgeMap.get(edge0);
    int[] b;
    if (o == null) {
      P3 coord2 = points[pt1];
      P3 coord1 = points[pt];
      vAB.sub2(coord2, coord1);
      for (int j = faceEdgeList.size(); --j >= 0;) {
        int[] e = faceEdgeList.get(j);
        P3 c1 = points[e[0]];
        P3 c2 = points[e[1]];
        if (c1 != coord1 && c1 != coord2 && c2 != coord1 && c2 != coord2
            && testDiff(c1, c2, coord1, coord2)
            && testDiff(coord1, coord2, c1, c2))
          return null;
      }
      return new Object[] { p1, Integer.valueOf(i), new int[] { pt, pt1 }, edge };
    }
    // set mask to exclude both of these.
    int[] p10 = (int[]) ((Object[]) o)[0];
    if (p10 == null)
      return null; // already seen
    int i0 = ((Integer) ((Object[]) o)[1]).intValue();
    p10[3] = -((-p10[3]) ^ (1 << i0));
    p1[3] = -((-p1[3]) ^ (1 << i));
    b = (int[]) ((Object[]) o)[2];
    for (int j = faceEdgeList.size(); --j >= 0;) {
      int[] f = faceEdgeList.get(j);
      if (f[0] == b[0] && f[1] == b[1]) {
        faceEdgeList.remove(j);
        break;
      }
    }
    htEdgeMap.put(edge, new Object[] { null });
    htEdgeMap.put(edge0, new Object[] { null });
    return Boolean.TRUE;
  }

  private boolean testDiff(P3 a1, P3 b1, P3 a2, P3 b2) {
    //
    //       b1
    //  a2_ /__b2
    //     /
    //    a1
    //
    vAB.sub2(b1, a1);
    vAC.sub2(a2, a1);
    vAC.cross(vAC, vAB);
    vBC.sub2(b2, a1);
    vBC.cross(vBC, vAB);
    return (vBC.dot(vAC) < 0);
  }

  private final V3 vAB = new V3();
  private final V3 vAC = new V3();
  private final V3 vBC = new V3();

  private static float MAX_DISTANCE_TO_PLANE = 0.1f;

  private boolean isPlanar(P3 pt1, P3 pt2, P3 pt3, P3 ptX) {
    /*
     * what is the quickest way to find out if four points are planar? 
     * here we determine the plane through three and then the distance to that plane
     * of the fourth
     * 
     */
    V3 norm = new V3();
    float w = Measure.getNormalThroughPoints(pt1, pt2, pt3, norm, vAB);
    float d = Measure.distanceToPlaneV(norm, w, ptX);
    return (Math.abs(d) < MAX_DISTANCE_TO_PLANE);
  }


  private int[][] getFaces(int[][] triangles, int triangleCount,
                           Map<Integer, Object[]> htNormMap) {
    int n = htNormMap.size();
    int[][] faces = AU.newInt2(n);
    if (triangleCount == n) {
      for (int i = triangleCount; --i >= 0;)
        faces[i] = AU.arrayCopyI(triangles[i], 3);
      return faces;
    }
    int fpt = 0;
    for (Entry<Integer, Object[]> e : htNormMap.entrySet()) {
      @SuppressWarnings("unchecked")
      Lst<int[]> faceEdgeList = (Lst<int[]>)e.getValue()[0];
      n = faceEdgeList.size();
      int[] face = faces[fpt++] = new int[n];
      if (n < 2)
        continue;
      int[] edge = faceEdgeList.get(0);
      //System.out.println("poly edge=" + PT.toJSON(null, edge));
      face[0] = edge[0];
      face[1] = edge[1];
      int pt = 2;
      int i0 = 1;
      int  pt0 =  -1;
      while (pt < n && pt0 != pt) {
        pt0 = pt;
        for (int i = i0; i < n; i++) {
          edge = faceEdgeList.get(i);
          if (edge[0] == face[pt - 1]) {
            face[pt++] = edge[1];
            if (i == i0)
              i0++;
            break;
          }
        }
      }
    }
    return faces;
  }

  @Override
  public void setModelVisibilityFlags(BS bsModels) {
    /*
    * set all fixed objects visible; others based on model being displayed note
    * that this is NOT done with atoms and bonds, because they have mads. When
    * you say "frame 0" it is just turning on all the mads.
    */
    for (int i = polyhedronCount; --i >= 0;) {
      Polyhedron p = polyhedrons[i];
      if (p.id == null) {
        int ia = p.centralAtom.i;
        if (ms.at[ia].isDeleted())
          p.isValid = false;
       p.visibilityFlags = (p.visible && bsModels.get(p.modelIndex)
            && !ms.isAtomHidden(ia)
            && !ms.at[ia].isDeleted() ? vf : 0);
        atoms[ia].setShapeVisibility(vf, p.visibilityFlags != 0);
      } else {
        p.visibilityFlags = (p.visible
            && (p.modelIndex < 0 || bsModels.get(p.modelIndex)) ? vf : 0);
      }
    }
  }

  @Override
  public String getShapeState() {
    if (polyhedronCount == 0)
      return "";
    SB s = new SB();
    for (int i = 0; i < polyhedronCount; i++)
      if (polyhedrons[i].isValid)
        s.append(polyhedrons[i].getState(vwr));
    if (drawEdges == EDGES_FRONT)
      appendCmd(s, "polyhedra frontedges");
    else if (drawEdges == EDGES_ALL)
      appendCmd(s, "polyhedra edges");
    s.append(vwr.getAtomShapeState(this));
    int ia;
    for (int i = 0; i < polyhedronCount; i++) {
      Polyhedron p = polyhedrons[i];
      if (p.isValid && p.id == null && p.colixEdge != C.INHERIT_ALL
          && bsColixSet.get(ia = p.centralAtom.i))
        appendCmd(
            s,
            "select ({"
                + ia
                + "}); color polyhedra "
                + (C.isColixTranslucent(colixes[ia]) ? "translucent "
                    : "") + C.getHexCode(colixes[ia]) + " "
                + C.getHexCode(p.colixEdge));
    }
    return s.toString();
  }
}
