package com.laundry.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Hand-written thread-safe generic FIFO Queue implemented on a singly linked list
 * with explicit head and tail pointers. Does not delegate to java.util.Queue.
 *
 * @param <T> element type stored in FIFO order
 */
public class LaundryQueue<T> {

    private static final class Node<T> {
        final T data;
        Node<T> next;

        Node(T data) {
            this.data = data;
        }
    }

    private Node<T> head;
    private Node<T> tail;
    private int size;

    public LaundryQueue() {
        this.head = null;
        this.tail = null;
        this.size = 0;
    }

    /**
     * Appends an element to the tail of the queue in O(1) time.
     */
    public synchronized void enqueue(T item) {
        if (item == null) {
            throw new IllegalArgumentException("Cannot enqueue null element");
        }
        Node<T> node = new Node<>(item);
        if (tail == null) {
            head = node;
            tail = node;
        } else {
            tail.next = node;
            tail = node;
        }
        size++;
    }

    /**
     * Removes and returns the head element in O(1) time, or null if empty.
     */
    public synchronized T dequeue() {
        if (head == null) {
            return null;
        }
        T data = head.data;
        head = head.next;
        if (head == null) {
            tail = null;
        }
        size--;
        return data;
    }

    /**
     * Returns the head element without removing it, or null if empty.
     */
    public synchronized T peek() {
        return head != null ? head.data : null;
    }

    public synchronized boolean isEmpty() {
        return size == 0;
    }

    public synchronized int size() {
        return size;
    }

    /**
     * Returns a defensive snapshot list from head to tail for display/serialization.
     */
    public synchronized List<T> toList() {
        List<T> snapshot = new ArrayList<>(size);
        Node<T> current = head;
        while (current != null) {
            snapshot.add(current.data);
            current = current.next;
        }
        return snapshot;
    }

    /**
     * Checks whether the specified element exists anywhere in the queue.
     */
    public synchronized boolean contains(T id) {
        Node<T> current = head;
        while (current != null) {
            if (Objects.equals(current.data, id)) {
                return true;
            }
            current = current.next;
        }
        return false;
    }

    /**
     * Removes the first matching element from anywhere in the queue (used for cancellations).
     *
     * @return true if the element was found and removed, false otherwise
     */
    public synchronized boolean remove(T id) {
        if (head == null) {
            return false;
        }
        if (Objects.equals(head.data, id)) {
            dequeue();
            return true;
        }
        Node<T> prev = head;
        Node<T> curr = head.next;
        while (curr != null) {
            if (Objects.equals(curr.data, id)) {
                prev.next = curr.next;
                if (curr == tail) {
                    tail = prev;
                }
                size--;
                return true;
            }
            prev = curr;
            curr = curr.next;
        }
        return false;
    }

    /**
     * Removes all elements from the queue.
     */
    public synchronized void clear() {
        head = null;
        tail = null;
        size = 0;
    }
}
