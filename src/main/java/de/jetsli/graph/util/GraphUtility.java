/*
 *  Copyright 2012 Peter Karich 
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.graph.util;

import de.jetsli.graph.coll.MyBitSet;
import de.jetsli.graph.coll.MyBitSetImpl;
import de.jetsli.graph.geohash.KeyAlgo;
import de.jetsli.graph.geohash.SpatialKeyAlgo;
import de.jetsli.graph.storage.Directory;
import de.jetsli.graph.storage.Edge;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.GraphStorage;
import de.jetsli.graph.storage.Location2IDPreciseIndex;
import de.jetsli.graph.storage.PriorityGraphStorage;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is introduced as a helper to avoid cluttering the Graph interface with all the common
 * methods. Most of the methods are useful for unit tests.
 *
 * @author Peter Karich,
 */
public class GraphUtility {

    private static Logger logger = LoggerFactory.getLogger(GraphUtility.class);

    /**
     * @throws could throw exception if uncatched problems like index out of bounds etc
     */
    public static List<String> getProblems(Graph g) {
        List<String> problems = new ArrayList<String>();
        int nodes = g.getNodes();
        for (int i = 0; i < nodes; i++) {
            double lat = g.getLatitude(i);
            if (lat > 90 || lat < -90)
                problems.add("latitude is not within its bounds " + lat);
            double lon = g.getLongitude(i);
            if (lon > 180 || lon < -180)
                problems.add("longitude is not within its bounds " + lon);
            int incom = count(g.getIncoming(i));
            int out = count(g.getOutgoing(i));
            int e = count(g.getEdges(i));
            if (Math.max(out, incom) > e)
                problems.add("count incoming or outgoing edges should be maximum "
                        + e + " but were:" + incom + "(in), " + out + "(out)");

            EdgeIterator iter = g.getEdges(i);
            while (iter.next()) {
                if (iter.node() >= nodes)
                    problems.add("edge of " + i + " has a node " + iter.node() + " greater or equal to getNodes");
                if (iter.node() < 0)
                    problems.add("edge of " + i + " has a negative node " + iter.node());
            }
        }

//        for (int i = 0; i < nodes; i++) {
//            new XFirstSearch().start(g, i, false);
//        }

        return problems;
    }

    /**
     * note/todo: this methods counts edges twice if both directions are available
     */
    public static int countEdges(Graph g) {
        int counter = 0;
        int nodes = g.getNodes();
        for (int i = 0; i < nodes; i++) {
            EdgeIterator iter = g.getOutgoing(i);
            while (iter.next()) {
                counter++;
            }
        }
        return counter;
    }

    public static int count(EdgeIterator iter) {
        int counter = 0;
        while (iter.next()) {
            ++counter;
        }
        return counter;
    }

    public static int count(Iterable<?> iter) {
        int counter = 0;
        for (Object o : iter) {
            ++counter;
        }
        return counter;
    }

    public static boolean contains(EdgeIterator iter, int... locs) {
        TIntHashSet set = new TIntHashSet();

        while (iter.next()) {
            set.add(iter.node());
        }
        for (int l : locs) {
            if (!set.contains(l))
                return false;
        }
        return true;
    }

    public static boolean contains(Iterable<? extends Edge> iter, int... locs) {
        Iterator<? extends Edge> i = iter.iterator();
        TIntHashSet set = new TIntHashSet();
        while (i.hasNext()) {
            set.add(i.next().node);
        }
        for (int l : locs) {
            if (!set.contains(l))
                return false;
        }
        return true;
    }

    public static EdgeIterator until(EdgeIterator edges, int node, int flags) {
        while (edges.next()) {
            if (edges.node() == node && edges.flags() == flags)
                return edges;
        }
        return EdgeIterator.EMPTY;
    }

    public static EdgeIterator until(EdgeIterator edges, int node) {
        while (edges.next()) {
            if (edges.node() == node)
                return edges;
        }
        return EdgeIterator.EMPTY;
    }

    /**
     * Added this helper method to avoid cluttering the graph interface. Good idea?
     */
    public static EdgeIterator getEdges(Graph graph, int index, boolean out) {
        if (out)
            return graph.getOutgoing(index);
        else
            return graph.getIncoming(index);
    }

    public static void printInfo(final Graph g, int startNode, final int counts) {
        new XFirstSearch() {
            int counter = 0;

            @Override protected boolean goFurther(int nodeId) {
                System.out.println(getNodeInfo(g, nodeId));
                if (counter++ > counts)
                    return false;
                return true;
            }
        }.start(g, startNode, false);
    }

    public static String getNodeInfo(Graph g, int nodeId) {
        EdgeIterator iter = g.getOutgoing(nodeId);
        String str = nodeId + ":" + g.getLatitude(nodeId) + "," + g.getLongitude(nodeId) + "\n";
        while (iter.next()) {
            str += "  ->" + iter.node() + "\t" + BitUtil.toBitString(iter.flags(), 8) + "\n";
        }
        return str;
    }

    public static Graph shuffle(final Graph g, Directory dir) {
        int len = g.getNodes();
        TIntList list = new TIntArrayList(len, -1);
        list.fill(0, len, -1);
        for (int i = 0; i < len; i++) {
            list.set(i, i);
        }
        list.shuffle(new Random());
        return createSortedGraph(g, dir, list);
    }

    public static Graph sortDFS(final Graph g, Directory dir) {
        final TIntList list = new TIntArrayList(g.getNodes(), -1);
        list.fill(0, g.getNodes(), -1);
        new XFirstSearch() {
            int counter = 0;

            @Override
            protected boolean goFurther(int nodeId) {
                list.set(nodeId, counter);
                counter++;
                return super.goFurther(nodeId);
            }
        }.start(g, 0, false);
        return createSortedGraph(g, dir, list);
    }

    // a lot memory is necessary 
    public static Graph sort(final Graph g, Directory dir, int capacity) {
        // make sure it is a square rootable number -> necessary for spatialkeyalgo
//        capacity = (int) Math.sqrt(capacity);
//        capacity *= capacity;

        int bits = (int) (Math.log(capacity) / Math.log(2));
        final KeyAlgo algo = new SpatialKeyAlgo(bits);
        Location2IDPreciseIndex index = new Location2IDPreciseIndex(g, dir) {
            @Override protected KeyAlgo createKeyAlgo(int latS, int lonS) {
                return algo;
            }
        };
        index.setCalcEdgeDistance(false);
        Location2IDPreciseIndex.InMemConstructionIndex idx = index.prepareInMemoryIndex(capacity);
        final TIntList mappingList = new TIntArrayList(g.getNodes(), -1);
        mappingList.fill(0, g.getNodes(), -1);
        int counter = 0;
        int tmp = 0;
        for (int ti = 0; ti < idx.getLength(); ti++) {
            TIntArrayList list = idx.getNodes(ti);
            if (list == null)
                continue;
            tmp++;
            int s = list.size();
            for (int ii = 0; ii < s; ii++) {
                mappingList.set(list.get(ii), counter);
                counter++;
            }
        }

        return createSortedGraph(g, dir, mappingList);
    }

    private static Graph createSortedGraph(final Graph g, Directory dir, final TIntList oldToNewList) {
        final GraphStorage sortedGraph = new PriorityGraphStorage(dir).createNew(g.getNodes());
        int len = oldToNewList.size();
        // important to avoid creating two edges for edges with both directions
        MyBitSet bitset = new MyBitSetImpl(len);
        for (int old = 0; old < len; old++) {
            int newIndex = oldToNewList.get(old);
            // ignore empty entries
            if (newIndex < 0)
                continue;
            bitset.add(newIndex);
            sortedGraph.setNode(newIndex, g.getLatitude(old), g.getLongitude(old));
            EdgeIterator eIter = g.getOutgoing(old);
            while (eIter.next()) {
                int newEdgeIndex = oldToNewList.get(eIter.node());
                if (bitset.contains(newEdgeIndex))
                    continue;
                sortedGraph.edge(newIndex, newEdgeIndex, eIter.distance(), eIter.flags());
            }
        }

        return sortedGraph;
    }
}