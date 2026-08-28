package com.ahheng.tile2d.util.intmap;

// int 键对象值映射表(跨平台)
// 供各存储容器选用,由具体实现决定哈希/稀疏/包装策略
public interface IntMap<V> {

    V get(int key); // 按键取值,不存在返回 null

    void put(int key, V value); // 写入键值对

    V remove(int key); // 按键删除,返回被删值(不存在返回 null)

    int size(); // 当前元素个数

    boolean containsKey(int key); // 是否包含指定键

    void clear(); // 清空全部元素

    default Iterator<V> iterator() {
        return iterator(false);
    }

    Iterator<V> iterator(boolean deleteMode); // 迭代器,deleteMode 开启时允许迭代中删除

    interface Iterator<V> {
        boolean next(); // 游标前移,返回是否还有下一个
        int key(); // 当前键
        V value(); // 当前值
        void remove(); // 删除当前项
    }

}
