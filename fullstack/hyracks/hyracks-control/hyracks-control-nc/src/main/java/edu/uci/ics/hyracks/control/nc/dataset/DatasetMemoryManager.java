/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.hyracks.control.nc.dataset;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import edu.uci.ics.hyracks.api.dataset.IDatasetPartitionWriter;
import edu.uci.ics.hyracks.api.dataset.Page;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.api.partitions.ResultSetPartitionId;

public class DatasetMemoryManager {
    private final Set<Page> availPages;

    private final LeastRecentlyUsedList leastRecentlyUsedList;

    private final Map<ResultSetPartitionId, PartitionNode> resultPartitionNodesMap;

    private final static int FRAME_SIZE = 32768;

    public DatasetMemoryManager(int availableMemory) {
        availPages = new HashSet<Page>();

        // Atleast have one page for temporarily storing the results.
        if (availableMemory <= 0)
            availableMemory = FRAME_SIZE;

        while (availableMemory >= FRAME_SIZE) {
            /* TODO(madhusudancs): Should we have some way of accounting this memory usage by using Hyrack's allocateFrame()
             * instead of direct ByteBuffer.allocate()?
             */
            availPages.add(new Page(ByteBuffer.allocate(FRAME_SIZE)));
            availableMemory -= FRAME_SIZE;
        }

        leastRecentlyUsedList = new LeastRecentlyUsedList();
        resultPartitionNodesMap = new HashMap<ResultSetPartitionId, PartitionNode>();
    }

    public Page requestPage(ResultSetPartitionId resultSetPartitionId, IDatasetPartitionWriter dpw)
            throws OutOfMemoryError, HyracksDataException {
        Page page;
        if (availPages.isEmpty()) {
            page = evictPage();
        } else {
            page = getAvailablePage();
        }

        page.clear();

        /*
         * It is extremely important to update the reference after obtaining the page because, in the cases where memory
         * manager is allocated only one page of memory, the front of the LRU list should not be created by the
         * update reference call before a page is pushed on to the element of the LRU list. So we first obtain the page,
         * then make a updateReference call which in turn creates a new node in the LRU list and then add the page to it.
         */
        PartitionNode pn = updateReference(resultSetPartitionId, dpw);
        pn.add(page);
        return page;
    }

    public void pageReferenced(ResultSetPartitionId resultSetPartitionId) {
        // When a page is referenced the dataset partition writer should already be known, so we pass null.
        updateReference(resultSetPartitionId, null);
    }

    public int getPageSize() {
        return FRAME_SIZE;
    }

    protected void insertPartitionNode(ResultSetPartitionId resultSetPartitionId, PartitionNode pn) {
        leastRecentlyUsedList.add(pn);
        resultPartitionNodesMap.put(resultSetPartitionId, pn);
    }

    protected synchronized PartitionNode updateReference(ResultSetPartitionId resultSetPartitionId,
            IDatasetPartitionWriter dpw) {
        PartitionNode pn = null;

        if (!resultPartitionNodesMap.containsKey(resultSetPartitionId)) {
            if (dpw != null) {
                pn = new PartitionNode(resultSetPartitionId, dpw);
                insertPartitionNode(resultSetPartitionId, pn);
            }
            return pn;
        }
        pn = resultPartitionNodesMap.get(resultSetPartitionId);
        leastRecentlyUsedList.remove(pn);
        insertPartitionNode(resultSetPartitionId, pn);

        return pn;
    }

    protected synchronized Page evictPage() throws HyracksDataException {
        PartitionNode pn = leastRecentlyUsedList.getFirst();
        IDatasetPartitionWriter dpw = pn.getDatasetPartitionWriter();
        Page page = dpw.returnPage();

        /* If the partition holding the pages breaks the contract by not returning the page or it has no page, just take
         * away all the pages allocated to it and add to the available pages set.
         */
        if (page == null) {
            availPages.addAll(pn);
            pn.clear();
            resultPartitionNodesMap.remove(pn.getResultSetPartitionId());
            leastRecentlyUsedList.remove(pn);

            /* Based on the assumption that if the dataset partition writer returned a null page, it should be lying about
             * the number of pages it holds in which case we just evict all the pages it holds and should thus be able to
             * add all those pages to available set and we have at least one page to allocate back.
             */
            page = getAvailablePage();
        } else {
            pn.remove(page);

            // If the partition no more holds any pages, remove it from the linked list and the hash map.
            if (pn.isEmpty()) {
                resultPartitionNodesMap.remove(pn.getResultSetPartitionId());
                leastRecentlyUsedList.remove(pn);
            }
        }

        return page;
    }

    protected synchronized Page getAvailablePage() {
        Iterator<Page> iter = availPages.iterator();
        Page page = iter.next();
        iter.remove();
        return page;
    }

    private class LeastRecentlyUsedList {
        private PartitionNode head;

        private PartitionNode tail;

        public LeastRecentlyUsedList() {
            head = null;
            tail = null;
        }

        public void add(PartitionNode node) {
            if (head == null) {
                head = tail = node;
                return;
            }
            tail.setNext(node);
            node.setPrev(tail);
            tail = node;
        }

        public void remove(PartitionNode node) {
            if ((node == head) && (node == tail)) {
                head = tail = null;
                return;
            } else if (node == head) {
                head = head.getNext();
                head.setPrev(null);
                return;
            } else if (node == tail) {
                tail = tail.getPrev();
                tail.setNext(null);
                return;
            } else {
                PartitionNode prev = node.getPrev();
                PartitionNode next = node.getNext();
                prev.setNext(next);
                next.setPrev(prev);
            }
        }

        public PartitionNode getFirst() {
            return head;
        }
    }

    private class PartitionNode extends HashSet<Page> {
        private static final long serialVersionUID = 1L;

        private final ResultSetPartitionId resultSetPartitionId;

        private final IDatasetPartitionWriter datasetPartitionWriter;

        private PartitionNode prev;

        private PartitionNode next;

        public PartitionNode(ResultSetPartitionId resultSetPartitionId, IDatasetPartitionWriter datasetPartitionWriter) {
            this.resultSetPartitionId = resultSetPartitionId;
            this.datasetPartitionWriter = datasetPartitionWriter;
            prev = null;
            next = null;
        }

        public ResultSetPartitionId getResultSetPartitionId() {
            return resultSetPartitionId;
        }

        public IDatasetPartitionWriter getDatasetPartitionWriter() {
            return datasetPartitionWriter;
        }

        public void setPrev(PartitionNode node) {
            prev = node;
        }

        public PartitionNode getPrev() {
            return prev;
        }

        public void setNext(PartitionNode node) {
            next = node;
        }

        public PartitionNode getNext() {
            return next;
        }
    }
}
