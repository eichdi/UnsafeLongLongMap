package com.company;

import sun.misc.Unsafe;

/**
 * Мапа работает по след логике:
 * Всю область помечаем как 0.
 * Делим выделенную область на две части.
 * В каждой из областей делим память по 3 * 8 байта для хранения трех long. Назавем эту связку node,
 * 1. Ключ 2 Значение 3 Ссылка на область памяти при коллизии или конец списка (-1)
 * Первая часть служит для быстрого доступа к области памяти по ключу через метод getNodeAddress.
 * Вторая часть служит для реализации списка. Если по ключу найдена нода, но ключи не равны друг другу в третьей области
 * находится ссылка на следующую ноду и повторяем действия.
 */

/**
 * Требуется написать LongLongMap который по произвольному long ключу хранить произвольное long значение
 * Важно: все данные (в том числе дополнительные, если их размер зависит от числа элементов)
 * требуется хранить в выделенном заранее блоке в разделяемой памяти, адрес и размер которого передается в конструкторе
 * для доступа к памяти напрямую необходимо (и достаточно) использовать следующие два метода:
 * sun.misc.Unsafe.getLong(long), sun.misc.Unsafe.putLong(long, long)
 */
public class LongLongMap {
    private static final int NODE_ELEMENTS = 3;
    private static final int HAS_ELEMENT_ADDRESS = -1;
    private static final int LIST_PROPORTION = 3; //Показывает лучший результат по заполняемости и коллизий

    public static final byte DEFAULT_VALUE = (byte) 0;
    /**
     * Размер битов в long
     */
    private static final int LONG_BYTE_SIZE = 8;

    private final Unsafe unsafe;
    private final long beginAddress;
    private long lastListCellAddress;
    private final long countListCells;
    private final long countNode;
    private long countListNode;

    /**
     * @param unsafe  для доступа к памяти
     * @param address адрес начала выделенной области памяти
     * @param size    размер выделенной области в байтах (~100GB)
     */
    LongLongMap(Unsafe unsafe, long address, long size) {
        this.unsafe = unsafe;
        beginAddress = unsafe.reallocateMemory(address, size);
        long countCells = size / LONG_BYTE_SIZE / NODE_ELEMENTS;
        countListCells = countCells / LIST_PROPORTION;
        countNode = countCells - countListCells;
        lastListCellAddress = beginAddress + (countNode * NODE_ELEMENTS * LONG_BYTE_SIZE);
        countListNode = 0;
        unsafe.setMemory(beginAddress, size, DEFAULT_VALUE);
    }


    /**
     * Метод должен работать со сложностью O(1) при отсутствии коллизий, но может деградировать при их появлении
     *
     * @param k произвольный ключ
     * @param v произвольное значение
     * @return предыдущее значение или 0
     */
    long put(long k, long v) {
        long nodeAddress = getNodeAddress(k);

        long keyAddress = getKeyAddress(nodeAddress);
        long valueAddress = getValueAddress(nodeAddress);
        long nextAddress = getNextAddress(nodeAddress);

        long oldKey = unsafe.getLong(keyAddress);
        long oldVal = unsafe.getLong(valueAddress);
        long nextAddressVal = unsafe.getLong(nextAddress);

        if (nextAddressVal == DEFAULT_VALUE) {
            unsafe.putLong(keyAddress, k);
            unsafe.putLong(valueAddress, v);
            unsafe.putLong(nextAddress, HAS_ELEMENT_ADDRESS);
        } else {

            if (oldKey == k) {
                unsafe.putLong(valueAddress, v);
            } else {
                if (countListNode < countListCells) {
                    long lastNodeOfList = getLastNode(nodeAddress);
                    long newNode = lastListCellAddress;

                    lastListCellAddress = lastListCellAddress + (long) LONG_BYTE_SIZE * NODE_ELEMENTS;
                    unsafe.putLong(getNextAddress(lastNodeOfList), newNode);

                    unsafe.putLong(getKeyAddress(newNode), k);
                    unsafe.putLong(getValueAddress(newNode), v);
                    unsafe.putLong(getNextAddress(newNode), HAS_ELEMENT_ADDRESS);
                    countListNode++;
                    return 0;
                } else {
                    System.out.println(toString());
                    throw new RuntimeException("Out of elements");
                }
            }
        }
        return oldVal;
    }

    /**
     * Получение последней ноды из связки нод
     * @param nodeAddress начальная нода
     * @return конечная нода
     */
    private long getLastNode(long nodeAddress) {
        long nextAddressVal = unsafe.getLong(getNextAddress(nodeAddress));
        while (nextAddressVal != HAS_ELEMENT_ADDRESS) {
            nodeAddress = nextAddressVal;
            nextAddressVal = unsafe.getLong(getNextAddress(nextAddressVal));
        }
        return nodeAddress;
    }

    private long getNextAddress(long nodeAddress) {
        return nodeAddress + LONG_BYTE_SIZE * 2;
    }

    private long getValueAddress(long nodeAddress) {
        return nodeAddress + LONG_BYTE_SIZE;
    }

    private long getKeyAddress(long nodeAddress) {
        return nodeAddress;
    }

    /**
     * Метод должен работать со сложностью O(1) при отсутствии коллизий, но может деградировать при их появлении
     *
     * @param k ключ
     * @return значение или 0
     */
    long get(long k) {
        long nodeAddress = getNodeAddress(k);
        return get(nodeAddress, k);
    }

    private long get(long nodeAddress, long k) {
        long key = unsafe.getLong(getKeyAddress(nodeAddress));
        if (key == k) {
            return unsafe.getLong(getValueAddress(nodeAddress));
        } else {
            long nextAddressVal = unsafe.getLong(getNextAddress(nodeAddress));
            while (nextAddressVal != HAS_ELEMENT_ADDRESS) {
                nodeAddress = nextAddressVal;
                key = unsafe.getLong(getKeyAddress(nodeAddress));
                if (key == k) {
                    return unsafe.getLong(getValueAddress(nodeAddress));
                } else {
                    nextAddressVal = unsafe.getLong(getNextAddress(nodeAddress));
                }
            }
        }
        return 0;
    }

    /**
     * Получение области памяти по ключу
     * Есть два способа получения
     * @param k - ключ
     * @return адресс памяти
     */
    private long getNodeAddress(long k) {
//        long shift = countNode - 1 & k;
        long shift = Math.abs(k % countNode);
        return beginAddress + (shift * LONG_BYTE_SIZE * NODE_ELEMENTS);
    }
}