# M1 浏览器验证

此目录只使用合成数据，不连接 Admin、Flow、数据库或真实附件。正式运行入口在后续 M3–M5 接入。

在项目根目录执行：

```bash
source ~/.nvm/nvm.sh && nvm use v24.21.0
node code-copilot/changes/forge-native-print/verification/serve.mjs
```

打开 `http://127.0.0.1:4318/`，依次点击 0/10/100/500 行。结果显示行数、页数、重复/溢出检查。`检查打印文档` 会创建和销毁独立 iframe，检查未缩放的输出 DOM。`打印` 调用浏览器打印；`DIALOG_OPENED` 仅表示客户端调用事件。

异常按钮验证缺少指定字体、损坏图片和超高明细。预期显示 `EXPECTED_FAILURE` 并禁用打印。切回正常数量可重新准备。

构建同一验证入口（包含所有 M1 渲染与运行模块）：

```bash
node code-copilot/changes/forge-native-print/verification/serve.mjs --build
```

输出至 `/private/tmp/forge-print-verification-dist`。使用完按 Ctrl+C 停止验证服务。不要将临时目录作为生产发布物。

2026-09-18 已用 Chromium 153 实际验证，记录见 [browser-results.json](browser-results.json)。已人工查看 10 行单据截图，中文、编码和表格边界显示正常；没有保存 PDF 或调用物理打印机。A4 100% 缩放、关闭浏览器额外页眉/页脚、罕见汉字覆盖和实机尺寸仍按 test-spec 的人工验收执行。

已知边界：首期只支持本地已安装字体及系统通用字体族；显式指定但未安装的字体会失败。`keepWithNext` 保持整个区块与下一区块首片一起，无法在一页容纳时明确失败。页眉/页脚 `repeat=false` 只在首/末页显示，仍为各页保留相同物理正文区域。

2026-09-18 目标纠正后，在 forge-admin 重新执行单测/构建及浏览器验证，实际结果见 [browser-results-forge-admin.json](browser-results-forge-admin.json)。原 browser-results.json 明确标注为 LawHub 来源记录，不混用两次结果。
