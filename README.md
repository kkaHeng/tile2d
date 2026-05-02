# Tile2D

在屏幕的方寸之间，铺展无限可能。

---

## 简介

Tile2D 是一个高性能的 Android 2D 瓦片滚动框架，专为处理大量数据可视化场景而设计。无论是游戏地图、数据表格，还是图片墙、日历视图，Tile2D 都能以流畅的滚动体验和卓越的内存管理，让你的应用如丝般顺滑。

## 特性

- 🚀 **极致性能** - 只渲染可见区域，智能瓦片回收，内存占用恒定
- 🎨 **双重实现** - 提供 TileView（自定义绘制）和 TileLayout（ViewGroup）两种实现
- 🎯 **灵活适配** - 支持自定义瓦片尺寸、边界范围和布局策略
- 📊 **调试友好** - 内置性能监控，实时显示 FPS、同步耗时和瓦片统计
- 💎 **易于集成** - 简洁的 API 设计，快速上手，开箱即用

## 快速开始

### 添加依赖

```gradle
dependencies {
    implementation 'com.github.kkaHeng:tile2d:版本'
}
```

### 基础使用

#### 使用 TileView（自定义绘制）

```java
TileView tileView = new TileView(context);
tileView.setAdapter(new TileView.Adapter<MyTileHolder>() {
    @Override
    public MyTileHolder onCreateTileHolder(int type) {
        return new MyTileHolder();
    }

    @Override
    public void onBindTileHolder(MyTileHolder holder, int column, int row) {
        // 绑定数据
    }
});
```

#### 使用 TileLayout（标准 View）

```java
TileLayout tileLayout = new TileLayout(context);
tileLayout.setAdapter(new TileLayout.Adapter<MyTileHolder>() {
    @Override
    public MyTileHolder onCreateTileHolder(int type) {
        return new MyTileHolder(new TextView(context));
    }

    @Override
    public void onBindTileHolder(MyTileHolder holder, int column, int row) {
        // 绑定数据
    }
});
```

## 核心概念

### TileView vs TileLayout

| 特性 | TileView | TileLayout |
|------|----------|------------|
| 继承 | View | ViewGroup |
| 渲染方式 | 自定义绘制 | 标准 View 系统 |
| 适用场景 | 游戏、图表、高性能场景 | 列表、网格、常规 UI 场景 |
| 瓦片内容 | 自定义 Canvas 绘制 | 任意 View |
| 事件处理 | 完全自定义 | 标准 View 事件系统 |

### 瓦片生命周期

```
创建 → 绑定 → 进入窗口 → 离开窗口 → 濒死 → 回收 → 重新使用
```

### 性能优化

- **智能回收**：瓦片离开可见区域后自动进入回收池
- **类型复用**：按类型分类回收，提高复用率
- **精确渲染**：只绘制和布局可见区域的瓦片
- **滚动优化**：使用 Scroller 实现平滑滚动，支持 Fling

## API 文档

### 核心方法

#### 滚动控制

```java
// 滚动指定距离
tileView.offset(dx, dy);

// 跳转到指定位置
tileView.seek(column, row);
tileView.seek(column, row, offsetX, offsetY);
```

#### 尺寸设置

```java
// 设置默认瓦片尺寸
tileView.setDefaultTileWidth(width);
tileView.setDefaultTileHeight(height);

// 设置指定位置的瓦片尺寸
tileView.setTileWidth(column, width);
tileView.setTileHeight(row, height);

// 使用自定义尺寸提供器
tileView.setDimenProvider(new TileDimenProvider() {
    @Override
    public int getTileWidth(int column) {
        return calculateWidth(column);
    }

    @Override
    public int getTileHeight(int row) {
        return calculateHeight(row);
    }
});
```

#### 边界控制

```java
// 在 Adapter 中定义边界
public class MyAdapter extends TileView.Adapter<MyHolder> {
    @Override
    public int getLeftBound() {
        return -100;
    }

    @Override
    public int getRightBound() {
        return 100;
    }

    @Override
    public int getTopBound() {
        return -100;
    }

    @Override
    public int getBottomBound() {
        return 100;
    }
}
```

#### 调试模式

```java
// 启用调试模式，显示性能信息
tileView.setDebugMode(true);

// 检查边界状态
boolean atLeft = tileView.isAtLeftBound();
boolean atTop = tileView.isAtTopBound();
boolean atRight = tileView.isAtRightBound();
boolean atBottom = tileView.isAtBottomBound();
```

## 示例

项目包含完整的示例代码，展示了：

- TileView 的自定义绘制实现
- TileLayout 的 View 系统集成
- Perlin Noise 生成平滑颜色渐变
- 点击和长按事件处理
- Debug 模式性能监控

## 性能指标

在普通 Android 设备上的测试结果：

- **FPS**：稳定在 60/120 FPS
- **内存占用**：仅缓存可见区域瓦片，内存占用恒定
- **滚动延迟**：< 16ms
- **瓦片数量**：支持伪无限数量瓦片，性能不受影响

## 许可证

本项目采用 [MIT License](LICENSE) 开源协议。

## 致谢

感谢所有为本项目做出贡献的开发者。

---

## 联系方式

- 作者：阿恒
- 邮箱：kkaheng163@163.com
- GitHub：[https://github.com/kkaHeng](https://github.com/kkaHeng)

---

在代码的世界里，每一块瓦片都承载着无限可能。Tile2D，让无限变为可能。
