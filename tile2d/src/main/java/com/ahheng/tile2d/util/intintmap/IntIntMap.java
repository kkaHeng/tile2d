package com.ahheng.tile2d.util.intintmap;

// int 键 int 值映射表(跨平台)
// 零装箱设计,供各存储容器选用,由具体实现决定哈希/稀疏/包装策略
public interface IntIntMap {

    int get(int key); // 按键取值,不存在返回 0(与默认值语义相同)

    int get(int key, int defaultValue); // 按键取值,不存在返回默认值

    void put(int key, int value); // 写入键值对

    int remove(int key); // 按键删除,返回被删值(不存在返回 0)

    int size(); // 当前元素个数

    boolean containsKey(int key); // 是否包含指定键

    void clear(); // 清空全部元素

    default Iterator iterator() {
        return iterator(false);
    }

    Iterator iterator(boolean deleteMode); // 迭代器,deleteMode 开启时允许迭代中删除

    interface Iterator {
        boolean next(); // 游标前移,返回是否还有下一个
        int key(); // 当前键
        int value(); // 当前值
        void remove(); // 删除当前项
    }

}