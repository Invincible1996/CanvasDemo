# Canvas Demo

一个基于Android自定义View的画布应用，支持图片加载、矩形标注等功能。

## 功能特点

- 图片加载和显示
- 手势缩放图片
- 矩形标注功能
  - 绘制模式：自由绘制矩形框
  - 编辑模式：移动已绘制的矩形框
- 撤销(Undo)上一次绘制的矩形
- 重做(Redo)已撤销的矩形
- 保存标注结果到图库
- 交互反馈
  - 当前选中的矩形会显示蓝色边框和半透明遮罩
  - 未选中的矩形显示红色边框

## 系统要求

- Android 6.0 (API Level 23) 或更高版本
- Android Studio Flamingo 或更高版本
- Kotlin 1.8+

## 快速开始

1. 克隆项目到本地：
```bash
git clone https://github.com/yourusername/CanvasDemo.git
```

2. 使用Android Studio打开项目
3. 点击"运行"按钮将应用安装到设备或模拟器上

## 使用说明

### 绘制模式
- 单指触摸并拖动来绘制矩形框
- 点击撤销按钮可以删除最后一个矩形
- 点击重做按钮可以恢复已删除的矩形

### 编辑模式
- 切换到编辑模式后，可以触摸并拖动已有的矩形进行位置调整
- 被选中的矩形会高亮显示

### 缩放操作
- 使用双指进行捏合手势可以缩放画布
- 缩放范围限制在0.1x到5.0x之间

### 保存功能
- 点击保存按钮将当前画布内容保存到系统图库
- 保存的图片包含所有标注的矩形框

## 技术实现

- 基于Android自定义View开发
- 使用Canvas进行绘制
- 实现ScaleGestureDetector处理缩放手势
- 支持多点触控处理
- 使用MediaStore API保存图片到系统图库

## 项目结构

```
app/
├── src/
│   ├── main/
│   │   ├── java/        # 源代码目录
│   │   ├── res/         # 资源文件
│   │   └── AndroidManifest.xml
│   ├── test/            # 单元测试
│   └── androidTest/     # UI测试
```

## 贡献指南

欢迎提交问题和改进建议！如果您想贡献代码：

1. Fork 本仓库
2. 创建您的特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交您的更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启一个 Pull Request

## 开源许可

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件