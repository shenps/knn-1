/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.mahout.knn.search;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Maps;
import org.apache.mahout.common.distance.DistanceMeasure;
import org.apache.mahout.knn.WeightedVector;
import org.apache.mahout.math.DenseVector;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.function.DoubleFunction;
import org.apache.mahout.math.function.Functions;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map;

/**
 * Does approximate nearest neighbor dudes search by projecting the data.
 */
public class ProjectionSearch3 extends Searcher {
    private final List<TreeSet<WeightedVector>> vectors;

    private DistanceMeasure distance;
    private List<Vector> basis;
    private int searchSize;

    public ProjectionSearch3(int d, DistanceMeasure distance, int projections, int searchSize) {
        this.searchSize = searchSize;
        Preconditions.checkArgument(projections > 0 && projections < 100, "Unreasonable value for number of projections");

        final DoubleFunction random = Functions.random();

        this.distance = distance;
        vectors = Lists.newArrayList();
        basis = Lists.newArrayList();

        // we want to create several projections.  Each is alike except for the
        // direction of the projection
        for (int i = 0; i < projections; i++) {
            // create a random vector to use for the basis of the projection
            final DenseVector projection = new DenseVector(d);
            projection.assign(random);
            projection.normalize();

            basis.add(projection);

            // the projection is implemented by a tree set where the ordering of vectors
            // is based on the dot product of the vector with the projection vector
            vectors.add(Sets.<WeightedVector>newTreeSet());
        }
    }

    /**
     * Adds a vector into the set of projections for later searching.
     * @param v  The vector to add.
     */
    public void add(Vector v) {
        // add to each projection separately
        Iterator<Vector> projections = basis.iterator();
        for (TreeSet<WeightedVector> s : vectors) {
            s.add(new WeightedVector(v, projections.next()));
        }
    }

    public List<WeightedVector> search(final Vector query, int n) {
        Map<Vector, Double> distances = Maps.newHashMap();

        // for each projection
        Iterator<Vector> projections = basis.iterator();
        for (TreeSet<WeightedVector> v : vectors) {
            WeightedVector projectedQuery = new WeightedVector(query, projections.next());

            // Collect nearby vectors
            List<WeightedVector> candidates = Lists.newArrayList();
            Iterables.addAll(candidates, Iterables.limit(v.tailSet(projectedQuery, true), searchSize));
            Iterables.addAll(candidates, Iterables.limit(v.headSet(projectedQuery, false).descendingSet(), searchSize));

            // find maximum projected distance in nearby values.
            // all unmentioned values will be at least that far away.
            // also collect a set of unmentioned values
            Set<Vector> unmentioned = Sets.newHashSet(distances.keySet());
            double maxDistance = 0;
            for (WeightedVector vector : candidates) {
                unmentioned.remove(vector.getVector());
                maxDistance = Math.max(maxDistance, vector.getWeight());
            }

            // all unmentioned vectors have to be put at least as far away as we can justify
            for (Vector vector : unmentioned) {
                double x = distances.get(vector);
                if (maxDistance > x) {
                    distances.put(vector, maxDistance);
                }
            }

            // and all candidates get a real test
            for (WeightedVector candidate : candidates) {
                Double x = distances.get(candidate);
                if (x == null || x < candidate.getWeight()) {
                    distances.put(candidate.getVector(), candidate.getWeight());
                }
            }
        }

        // now sort by actual distance
        List<WeightedVector> r = Lists.newArrayList();
        for (Vector vector : distances.keySet()) {
            r.add(new WeightedVector(vector, distance.distance(query, vector)));
        }

        Collections.sort(r);
        return r.subList(0, n);
    }

    @Override
    public int size() {
        return vectors.get(0).size();
    }

    @Override
    public int getSearchSize() {
        return searchSize;
    }

    @Override
    public void setSearchSize(int searchSize) {
        this.searchSize = searchSize;
    }


}
