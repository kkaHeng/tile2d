package com.ahheng.tile2d.app.data;

import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.app.BaseActivity;
import com.ahheng.tile2d.dimen.DragResizerView;
import com.ahheng.tile2d.dimen.Measurable;
import com.ahheng.tile2d.dimen.MeasurableDimenProvider;
import com.ahheng.tile2d.widget.canvas.TileView;
import com.ahheng.tile2d.widget.layout.TileLayout;

public class TableActivity extends BaseActivity {

    private TileLayout tileLayout;
    private DataAdapter adapter;
    private MeasurableDimenProvider dimenProvider;

    private DragResizerView dragResizerView;
    private long currTileHolder;
    private boolean isSelected;

    String[][] data = {
            // 表头行：20个对比维度
            {
                    "语言名称", "首次发布年份", "主要设计者/组织", "编程范式",
                    "类型系统", "内存管理", "执行方式", "主要用途领域",
                    "TIOBE排名(2026)", "ISO标准", "开源许可", "典型扩展名",
                    "包管理器", "并发模型", "跨平台", "学习曲线",
                    "平均薪资(万美元)", "代表框架/库", "编译器/解释器数", "活跃指数"
            },
            // === 通用系统与主流语言 ===
            {
                    "C", "1972", "Dennis Ritchie / Bell Labs", "过程式、结构化",
                    "静态弱类型", "手动", "编译(AOT)", "系统编程、嵌入式、OS内核",
                    "2", "是(C11/C17)", "BSD/MIT", ".c/.h",
                    "无(系统级)", "无内置", "是", "陡峭",
                    "12.5", "Linux内核、SQLite、OpenSSL", "15+", "极高"
            },
            {
                    "C++", "1985", "Bjarne Stroustrup / Bell Labs", "多范式(过程式、OOP、泛型、函数式)",
                    "静态强类型", "手动+RAII+智能指针", "编译(AOT)", "系统编程、游戏、高频交易、桌面应用",
                    "3", "是(C++20/23)", "BSD/MIT", ".cpp/.hpp/.h",
                    "Conan/vcpkg", "线程+协程(C++20)", "是", "陡峭",
                    "13.0", "Qt、Boost、Unreal Engine、STL", "12+", "极高"
            },
            {
                    "Java", "1995", "James Gosling / Sun Microsystems", "面向对象、泛型、函数式",
                    "静态强类型", "自动垃圾回收(GC)", "编译+JVM解释(JIT)", "企业应用、Android、大数据、后端",
                    "4", "否(Oracle主导)", "GPL+商业", ".java/.class/.jar",
                    "Maven/Gradle", "线程+并发包+虚拟线程", "是(Write Once Run Anywhere)", "中等",
                    "12.0", "Spring、Hibernate、Apache Commons", "5+", "极高"
            },
            {
                    "Python", "1991", "Guido van Rossum / CWI", "多范式(OOP、函数式、过程式、脚本)",
                    "动态强类型(可选类型提示)", "自动垃圾回收(GC)", "解释执行(CPython)+JIT(PyPy)", "AI/ML、数据科学、Web、自动化、教育",
                    "1", "否(PSF主导)", "PSF", ".py/.pyw/.pyc",
                    "pip/conda/poetry", "GIL(全局解释器锁)+多进程+asyncio", "是", "平缓",
                    "11.5", "TensorFlow、PyTorch、Django、Pandas", "10+", "极高"
            },
            {
                    "JavaScript", "1995", "Brendan Eich / Netscape", "多范式(事件驱动、函数式、OOP、原型)",
                    "动态弱类型", "自动垃圾回收(GC)", "解释+JIT(V8/SpiderMonkey)", "Web前端、Node.js后端、跨平台移动",
                    "7", "否(ECMA标准)", "MIT/BSD", ".js/.mjs/.jsx",
                    "npm/yarn/pnpm", "事件循环+Promise+async/await+Worker", "是", "平缓",
                    "11.0", "React、Vue、Angular、Express、Next.js", "15+", "极高"
            },
            {
                    "C#", "2000", "Anders Hejlsberg / Microsoft", "多范式(OOP、函数式、泛型、异步)",
                    "静态强类型(可推断)", "自动垃圾回收(GC)", "编译+CLR(JIT/AOT)", "企业应用、游戏(Unity)、Windows应用、云",
                    "5", "是(ECMA-334/ISO/IEC 23270)", "MIT", ".cs",
                    "NuGet", "async/await+Task+线程池", "是(.NET Core后)", "中等",
                    "11.5", ".NET、ASP.NET Core、Unity、Entity Framework", "5+", "极高"
            },
            {
                    "Go", "2009", "Robert Griesemer等 / Google", "过程式、并发优先、结构化",
                    "静态强类型(可推断)", "自动垃圾回收(GC)", "编译(AOT)", "云原生、微服务、DevOps工具、网络编程",
                    "8", "否", "BSD", ".go",
                    "go modules", "Goroutine+Channel(CSP模型)", "是", "平缓",
                    "13.5", "Kubernetes、Docker、Terraform、Gin、Echo", "3+", "高"
            },
            {
                    "Rust", "2010", "Graydon Hoare / Mozilla", "多范式(函数式、OOP、并发、系统)",
                    "静态强类型(所有权系统)", "所有权+借用检查器(无GC)", "编译(AOT)", "系统编程、WebAssembly、区块链、CLI工具",
                    "17", "否", "MIT/Apache-2.0", ".rs",
                    "Cargo", "所有权+Send/Sync+async/await", "是", "陡峭",
                    "14.0", "Tokio、Actix、Rocket、Bevy、Tauri", "3+", "高"
            },
            {
                    "Swift", "2014", "Chris Lattner / Apple", "多范式(OOP、函数式、协议导向)",
                    "静态强类型(可推断)", "自动引用计数(ARC)", "编译(AOT)+解释(REPL)", "iOS/macOS/watchOS/tvOS开发、服务端",
                    "16", "否", "Apache-2.0", ".swift",
                    "Swift Package Manager", "GCD+async/await+Actor", "是(Linux/Windows)", "中等",
                    "12.5", "SwiftUI、Vapor、Combine、Alamofire", "2+", "中高"
            },
            {
                    "Kotlin", "2011", "JetBrains", "多范式(OOP、函数式、协程)",
                    "静态强类型(可推断)", "自动垃圾回收(JVM GC)", "编译(JVM/Native/JS)", "Android、服务端、多平台、脚本",
                    "18", "否", "Apache-2.0", ".kt/.kts",
                    "Gradle/Maven", "协程+Flow+Channel", "是", "平缓",
                    "12.0", "Ktor、Jetpack Compose、kotlinx.coroutines", "3+", "高"
            },
            {
                    "Ruby", "1995", "Yukihiro Matsumoto", "纯面向对象、函数式、元编程",
                    "动态强类型", "自动垃圾回收(GC)", "解释执行(MRI/YARV)+JIT", "Web开发、DevOps、脚本、原型",
                    "19", "否", "Ruby License/BSD", ".rb",
                    "RubyGems/Bundler", "线程+Fiber+async(Ractor实验)", "是", "平缓",
                    "13.0", "Ruby on Rails、Sinatra、RSpec、Sidekiq", "8+", "中"
            },
            {
                    "PHP", "1995", "Rasmus Lerdorf", "过程式、OOP、函数式",
                    "动态弱类型(可选类型)", "自动垃圾回收(GC)", "解释+JIT(PHP 8+)", "Web后端、CMS、电商",
                    "10", "否", "PHP License", ".php",
                    "Composer", "多进程+异步(Swoole/ReactPHP)", "是", "平缓",
                    "9.5", "Laravel、Symfony、WordPress、Drupal", "4+", "中"
            },
            {
                    "TypeScript", "2012", "Anders Hejlsberg / Microsoft", "多范式(结构化类型、OOP、函数式)",
                    "静态强类型(渐进式)", "自动垃圾回收(GC)", "编译转译(tsc)为JS", "大型Web应用、Node.js、全栈",
                    "N/A(超集)", "否", "Apache-2.0", ".ts/.tsx",
                    "npm/yarn", "同JavaScript(事件循环+Promise)", "是", "中等",
                    "12.0", "React+TS、Vue+TS、NestJS、Deno", "2+", "极高"
            },
            {
                    "Scala", "2004", "Martin Odersky / EPFL", "多范式(函数式、OOP、并发)",
                    "静态强类型(类型推断)", "自动垃圾回收(JVM GC)", "编译(JVM/Native/JS)", "大数据、分布式系统、Web后端、金融",
                    "32", "否", "Apache-2.0", ".scala",
                    "sbt/Maven/Gradle", "Actor(Akka)+Future+async", "是", "陡峭",
                    "13.5", "Akka、Play Framework、Spark、Cats", "4+", "中"
            },
            {
                    "R", "1993", "Ross Ihaka & Robert Gentleman", "函数式、过程式、面向对象",
                    "动态弱类型", "自动垃圾回收(GC)", "解释执行", "统计分析、数据可视化、生物信息、学术研究",
                    "12", "否", "GPL-2/GPL-3", ".r/.R",
                    "CRAN/packrat/renv", "无内置(包级并行)", "是", "中等",
                    "11.0", "ggplot2、dplyr、shiny、caret、tidyverse", "3+", "中高"
            },
            {
                    "MATLAB", "1984", "Cleve Moler / MathWorks", "过程式、OOP、向量化",
                    "动态弱类型", "自动垃圾回收", "解释+JIT", "工程计算、信号处理、控制理论、仿真",
                    "14", "否", "商业专有", ".m",
                    "MATLAB Add-Ons", "并行计算工具箱+GPU+SPMD", "是(跨OS)", "中等",
                    "10.5", "Simulink、Image Processing、Optimization", "1", "中"
            },
            {
                    "Perl", "1987", "Larry Wall", "多范式(过程式、OOP、函数式、脚本)",
                    "动态弱类型", "自动垃圾回收(GC)", "解释执行", "系统管理、文本处理、CGI、生物信息",
                    "23", "否", "Artistic/GPL", ".pl/.pm",
                    "CPAN", "线程+fork+异步", "是", "平缓",
                    "10.5", "Mojolicious、DBI、BioPerl、Catalyst", "3+", "低"
            },
            {
                    "Lua", "1993", "Roberto Ierusalimschy等 / PUC-Rio", "多范式(过程式、函数式、脚本、元表)",
                    "动态弱类型", "自动垃圾回收(增量)", "解释执行+JIT(LuaJIT)", "游戏脚本、嵌入式、配置、Web",
                    "24", "否", "MIT", ".lua",
                    "LuaRocks", "协程(coroutine)+无原生线程", "是", "平缓",
                    "10.0", "OpenResty、Love2D、Torch、Neovim插件", "5+", "中"
            },
            {
                    "Haskell", "1990", "Lennart Augustsson等", "纯函数式、惰性求值",
                    "静态强类型(类型类、高阶类型)", "自动垃圾回收(GC)", "编译(GHC)+解释(GHCi)", "学术研究、金融、编译器、形式验证",
                    "33", "否", "BSD", ".hs/.lhs",
                    "Cabal/Stack", "Software Transactional Memory(STM)+async", "是", "陡峭",
                    "13.5", "Yesod、Snap、Pandoc、Xmonad、GHC", "3+", "中低"
            },
            {
                    "Erlang", "1986", "Joe Armstrong等 / Ericsson", "函数式、并发、容错、Actor模型",
                    "动态强类型", "自动垃圾回收(分代)", "编译(BEAM字节码)+解释", "电信、即时通讯、分布式系统、高可用",
                    "34", "否", "Apache-2.0", ".erl/.hrl",
                    "rebar3/hex", "Actor(轻量进程)+OTP+容错", "是", "陡峭",
                    "13.0", "OTP、RabbitMQ、CouchDB、WhatsApp后端", "3+", "中"
            },
            {
                    "Elixir", "2011", "José Valim", "函数式、并发、元编程",
                    "动态强类型", "自动垃圾回收(BEAM)", "编译(BEAM字节码)", "Web实时、分布式、物联网、区块链",
                    "28", "否", "Apache-2.0", ".ex/.exs",
                    "Mix/Hex", "Actor+OTP+GenServer+Task", "是", "中等",
                    "12.5", "Phoenix、Ecto、Nerves、Absinthe(GraphQL)", "2+", "中"
            },
            {
                    "Clojure", "2007", "Rich Hickey", "函数式(Lisp)、并发、持久化数据结构",
                    "动态强类型(可选类型)", "自动垃圾回收(JVM GC)", "编译(JVM字节码)+解释", "大数据、Web、金融、工具",
                    "38", "否", "EPL-1.0", ".clj/.cljs",
                    "Leiningen/Boot/tools.deps", "STM+Agent+core.async(CSP)", "是", "陡峭",
                    "13.0", "Ring、Compojure、Datomic、Incanter", "4+", "中低"
            },
            {
                    "F#", "2005", "Don Syme / Microsoft Research", "函数式、OOP、异步",
                    "静态强类型(类型推断)", "自动垃圾回收(.NET GC)", "编译(CLR)+解释(FSI)", "金融、数据科学、Web、游戏",
                    "40", "否", "Apache-2.0", ".fs/.fsx",
                    "NuGet/Paket", "async+MailboxProcessor+Task", "是", "中等",
                    "12.5", "Giraffe、Fable、Saturn、FsLab、Deedle", "3+", "中低"
            },
            {
                    "Dart", "2011", "Lars Bak & Kasper Lund / Google", "多范式(OOP、函数式、异步)",
                    "静态强类型(可选/可推断)", "自动垃圾回收(GC)", "编译(AOT/JIT)+解释", "Flutter跨平台、Web、服务端",
                    "25", "否", "BSD", ".dart",
                    "pub", "Isolate+async/await+Stream", "是", "平缓",
                    "11.5", "Flutter、AngularDart、Shelf、Riverpod", "3+", "中高"
            },
            {
                    "Julia", "2012", "Jeff Bezanson等 / MIT", "多范式(技术计算、函数式、OOP、分布式)",
                    "动态强类型(多派发)", "自动垃圾回收(增量)", "JIT编译(LLVM)", "科学计算、AI、数值分析、仿真",
                    "26", "否", "MIT", ".jl",
                    "Pkg(内置)", "Task+Channel+Distributed+GPU", "是", "中等",
                    "12.0", "Flux、DifferentialEquations、DataFrames、Plots", "3+", "中"
            },
            {
                    "Objective-C", "1984", "Brad Cox / Stepstone", "面向对象(消息传递)、过程式",
                    "动态弱类型", "自动引用计数(ARC)", "编译(AOT)", "macOS/iOS传统开发、遗留系统",
                    "20", "否", "Apple", ".m/.h",
                    "CocoaPods/SPM", "GCD+NSOperation+线程", "是(Apple生态)", "中等",
                    "11.5", "Cocoa、UIKit、Foundation、CoreData", "2+", "中低"
            },
            {
                    "Fortran", "1957", "John Backus / IBM", "过程式、数组、并行",
                    "静态强类型", "手动(现代自动)", "编译(AOT)", "科学计算、气象、物理模拟、HPC",
                    "36", "是(Fortran 2018)", "专有/开源", ".f90/.f/.for",
                    "fpm/CMake", "OpenMP+MPI+Coarray", "是", "陡峭",
                    "11.0", "LAPACK、BLAS、NetCDF、PETSc", "10+", "低"
            },
            {
                    "COBOL", "1959", "Grace Hopper等 / CODASYL", "过程式、结构化、面向对象(现代)",
                    "静态强类型", "手动", "编译(AOT)", "银行、保险、政府、遗留系统",
                    "41", "是(ISO 1989)", "专有", ".cob/.cbl",
                    "无", "无内置(批处理级)", "是", "陡峭",
                    "9.0", "CICS、DB2、IMS、Micro Focus", "5+", "极低"
            },
            {
                    "Lisp", "1958", "John McCarthy / MIT", "函数式、元编程、符号计算",
                    "动态强类型", "自动垃圾回收", "解释+编译", "AI、学术研究、Emacs扩展、配置",
                    "35", "是(ANSI Common Lisp)", "BSD/MIT", ".lisp/.cl",
                    "Quicklisp/ASDF", "线程+future(实现依赖)", "是", "陡峭",
                    "11.5", "SBCL、Emacs Lisp、Racket、CLISP", "15+", "低"
            },
            {
                    "Prolog", "1972", "Alain Colmerauer等 / Marseille", "逻辑式、声明式",
                    "动态强类型", "自动垃圾回收", "解释+编译(WAM)", "AI、专家系统、自然语言、约束求解",
                    "42", "是(ISO Prolog)", "LGPL/GPL", ".pl/.pro",
                    "SWI-Prolog包", "多线程+tabling+约束", "是", "陡峭",
                    "10.5", "SWI-Prolog、SICStus、GNU Prolog", "5+", "极低"
            },
            {
                    "Assembly", "1949", "Various", "低级、过程式、指令式",
                    "无类型(寄存器/内存)", "手动", "汇编(直接转机器码)", "嵌入式、驱动、引导程序、逆向、性能关键",
                    "N/A", "否", "公有领域", ".asm/.s/.nasm",
                    "无", "无(指令级并行)", "是(架构相关)", "极陡峭",
                    "11.0", "NASM、MASM、GAS、FASM", "50+", "低"
            },
            {
                    "SQL", "1974", "Donald Chamberlin & Raymond Boyce / IBM", "声明式、集合操作",
                    "静态类型(表结构)", "自动(事务管理)", "解释+查询优化", "数据库查询、数据分析、后端",
                    "N/A", "是(ISO/IEC 9075)", "专有/开源", ".sql",
                    "无(数据库自带)", "无(并发由DBMS处理)", "是", "平缓",
                    "10.5", "MySQL、PostgreSQL、SQLite、Oracle、SQL Server", "20+", "极高"
            },
            {
                    "Bash/Shell", "1989", "Brian Fox / GNU", "脚本、过程式、命令式",
                    "无类型(字符串)", "手动", "解释执行", "系统管理、自动化、DevOps、CI/CD",
                    "N/A", "否", "GPL", ".sh/.bash",
                    "无(系统包)", "后台作业+并行(&)", "是(Unix/Linux)", "平缓",
                    "10.0", "GNU Bash、Zsh、Fish、PowerShell", "10+", "高"
            },
            {
                    "PowerShell", "2006", "Jeffrey Snover / Microsoft", "脚本、OOP、函数式、命令式",
                    "动态类型(.NET对象)", "自动(.NET GC)", "解释+编译", "Windows管理、Azure、DevOps、自动化",
                    "N/A", "否", "MIT", ".ps1/.psm1",
                    "PowerShell Gallery", "RunspacePool+Job+Thread", "是(跨平台PS Core)", "中等",
                    "10.5", "Azure PowerShell、Pester、PSReadLine", "2+", "中"
            },
            {
                    "Groovy", "2003", "James Strachan / Apache", "多范式(OOP、函数式、脚本、元编程)",
                    "动态强类型(可选静态)", "自动垃圾回收(JVM GC)", "编译(JVM字节码)+解释", "Gradle脚本、测试、Web、DevOps",
                    "43", "否", "Apache-2.0", ".groovy",
                    "Gradle/Maven", "线程+GPars(并行)", "是", "平缓",
                    "11.0", "Grails、Spock、Gradle DSL、Ratpack", "3+", "中低"
            },
            {
                    "Crystal", "2014", "Ary Borenszweig等", "面向对象、函数式、并发",
                    "静态强类型(类型推断)", "自动垃圾回收", "编译(LLVM)", "Web、CLI工具、系统脚本",
                    "44", "否", "Apache-2.0", ".cr",
                    "Shards", "Fiber+Channel(CSP)", "是", "中等",
                    "11.5", "Kemal、Amber、Lucky、Granite", "2+", "低"
            },
            {
                    "Nim", "2008", "Andreas Rumpf", "多范式(过程式、函数式、OOP、元编程)",
                    "静态强类型(类型推断)", "可选GC/ARC/ORC/手动", "编译(C/C++/JS)", "系统编程、Web、游戏、脚本",
                    "45", "否", "MIT", ".nim",
                    "Nimble", "async/await+线程+Channel", "是", "中等",
                    "11.5", "Jester、Karax、NimForum、Nimbus", "3+", "低"
            },
            {
                    "Zig", "2016", "Andrew Kelley", "过程式、系统级、C替代",
                    "静态强类型(显式)", "手动+可选GC(comptime)", "编译(LLVM)", "系统编程、嵌入式、交叉编译、构建系统",
                    "46", "否", "MIT", ".zig",
                    "内置包管理", "线程+事件循环+async(实验)", "是", "陡峭",
                    "12.0", "Zig标准库、TigerBeetle、Bun(部分)", "2+", "中低"
            },
            {
                    "OCaml", "1996", "INRIA", "函数式、OOP、模块化",
                    "静态强类型(类型推断+多态)", "自动垃圾回收", "编译(AOT/字节码)+解释", "编译器、形式验证、金融、系统工具",
                    "37", "否", "LGPL/QPL", ".ml/.mli",
                    "opam/dune", "线程+Lwt+Async", "是", "陡峭",
                    "12.5", "Coq、Jane Street、MirageOS、BuckleScript", "4+", "中低"
            },
            {
                    "Scheme", "1975", "Guy Steele & Gerald Sussman / MIT", "函数式、元编程、教学",
                    "动态强类型", "自动垃圾回收", "解释+编译", "教学、学术研究、嵌入式脚本、Emacs",
                    "47", "否(R5RS/R6RS/R7RS)", "MIT/BSD", ".scm/.ss",
                    "无(Chicken Eggs等)", "call/cc+线程(实现依赖)", "是", "中等",
                    "10.5", "Racket、Guile、Chicken、MIT Scheme", "10+", "极低"
            },
            {
                    "Smalltalk", "1972", "Alan Kay等 / Xerox PARC", "纯面向对象、消息传递、动态",
                    "动态强类型", "自动垃圾回收", "解释+JIT", "教育、研究、金融、遗留系统",
                    "48", "否(ANSI)", "MIT/X11", ".st",
                    "Monticello/Metacello", "绿色线程+并发(实现依赖)", "是", "中等",
                    "10.5", "Squeak、Pharo、GNU Smalltalk、VisualWorks", "5+", "极低"
            },
            {
                    "Ada", "1980", "Jean Ichbiah / US DoD", "结构化、OOP、并发、实时",
                    "静态强类型(约束类型)", "手动+可选GC", "编译(AOT)", "航空航天、国防、铁路、医疗、嵌入式",
                    "29", "是(Ada 2012/2022)", "GPL+专有", ".adb/.ads",
                    "Alire", "任务(Task)+保护对象+Ravenscar", "是", "陡峭",
                    "11.5", "AdaCore GNAT、SPARK、PolyORB", "5+", "低"
            },
            {
                    "Pascal", "1970", "Niklaus Wirth / ETH Zurich", "过程式、结构化、OOP(现代)",
                    "静态强类型", "手动(现代自动)", "编译(AOT)", "教育、嵌入式、遗留系统、桌面",
                    "30", "否(ISO 7185)", "BSD/GPL", ".pas/.pp",
                    "无(系统包)", "线程(实现依赖)", "是", "中等",
                    "9.5", "Free Pascal、Lazarus、Delphi、Turbo Pascal", "5+", "低"
            },
            {
                    "Delphi/Object Pascal", "1995", "Borland/Embarcadero", "面向对象、事件驱动、组件",
                    "静态强类型", "手动+接口引用计数", "编译(AOT)", "Windows桌面、企业应用、移动(FMX)",
                    "31", "否", "商业专有", ".pas/.dpr",
                    "GetIt包管理", "线程+TTask+并行库", "是(跨平台FMX)", "中等",
                    "10.0", "VCL、FMX、FireDAC、Indy", "2+", "中低"
            },
            {
                    "VB.NET", "2001", "Microsoft", "面向对象、事件驱动、结构化",
                    "静态强类型(可推断)", "自动垃圾回收(.NET GC)", "编译(CLR)", "Windows应用、企业、遗留迁移",
                    "21", "否", "MIT", ".vb",
                    "NuGet", "async/await+Task+线程", "是(.NET Core)", "平缓",
                    "9.5", "WinForms、WPF、ASP.NET、Entity Framework", "2+", "低"
            },
            {
                    "D", "2001", "Walter Bright / Digital Mars", "多范式(过程式、OOP、函数式、元编程)",
                    "静态强类型", "自动GC(可选手动)", "编译(AOT)", "系统编程、游戏、Web、工具",
                    "27", "否", "Boost", ".d",
                    "Dub", "线程+Fiber+async/await", "是", "中等",
                    "11.5", "Vibe.d、Mir、DWT、Derelict", "4+", "低"
            },
            {
                    "Vala", "2006", "Jürg Billeter等 / GNOME", "面向对象、现代、C#风格",
                    "静态强类型", "自动引用计数", "编译转C+GObject", "Linux桌面、GNOME应用、系统工具",
                    "49", "否", "LGPL", ".vala",
                    "无(系统包)", "线程+异步", "是(Linux为主)", "平缓",
                    "9.5", "GTK、Libadwaita、Granite、Valadoc", "2+", "极低"
            },
            {
                    "V", "2019", "Alexander Medvednikov", "多范式(过程式、OOP、函数式)",
                    "静态强类型(可推断)", "可选GC/ARC/手动", "编译(AOT)", "系统编程、Web、CLI、游戏",
                    "50", "否", "MIT", ".v",
                    "vpm", "线程+Channel+async", "是", "平缓",
                    "11.0", "VWeb、V UI、V ORM、V Test", "2+", "低"
            },
            {
                    "Carbon", "2022", "Google", "多范式(系统、OOP、泛型)",
                    "静态强类型(可推断)", "手动(设计目标)", "编译(AOT)", "C++替代、系统编程、高性能",
                    "实验性", "否", "Apache-2.0", ".carbon",
                    "内置(计划中)", "线程(开发中)", "是(计划中)", "陡峭",
                    "N/A", "Carbon标准库(早期)", "1+", "极低"
            },
            // === 游戏引擎脚本与着色器语言 ===
            {
                    "GDScript", "2014", "Juan Linietsky等 / Godot社区", "面向对象、脚本、渐进式类型",
                    "动态可选静态类型", "引用计数+GC", "解释(Godot VM)", "游戏开发(Godot引擎)、交互应用",
                    "N/A", "否", "MIT", ".gd",
                    "Godot Asset Library", "无内置多线程(协程为主)", "是", "平缓",
                    "10.0", "Godot Engine、GDExtension", "1(Godot内置)", "高"
            },
            {
                    "GML", "1999", "Mark Overmars / YoYo Games", "过程式、脚本、事件驱动",
                    "动态弱类型", "自动GC", "解释(YoYo Runner)", "游戏开发(GameMaker Studio)、2D游戏",
                    "N/A", "否", "专有", ".gml/.yy",
                    "无", "无内置并发", "是(导出多平台)", "平缓",
                    "9.0", "GameMaker Studio、YoYo Compiler", "1", "中"
            },
            {
                    "Haxe", "2005", "Nicolas Cannasse / Motion-Twin", "多范式(OOP、函数式、泛型、元编程)",
                    "静态强类型(类型推断)", "自动GC(目标平台)", "编译(多目标:JS/C++/Java等)", "游戏、跨平台应用、Web、工具",
                    "N/A", "否", "MIT", ".hx",
                    "Haxelib", "线程+async(目标平台依赖)", "是", "中等",
                    "11.0", "OpenFL、Heaps、Kha、Lime、HaxeFlixel", "5+", "中"
            },
            {
                    "ActionScript", "1998", "Gary Grossman / Macromedia/Adobe", "面向对象、事件驱动、ECMAScript",
                    "动态弱类型(可选静态)", "自动GC", "编译(AVM字节码)+解释", "Web游戏、RIA、Flash/AIR(历史)",
                    "N/A", "否(ECMA-357)", "专有", ".as",
                    "无", "事件循环+Worker", "是(Flash Player)", "平缓",
                    "10.0", "Flash Player、Flex、AIR、Starling、Away3D", "3+", "极低(已废弃)"
            },
            {
                    "Wren", "2014", "Robert Nystrom", "脚本、面向对象、函数式",
                    "动态强类型", "自动GC", "解释(Wren VM)", "游戏脚本、嵌入、教育、配置",
                    "N/A", "否", "MIT", ".wren",
                    "无", "纤程(Fiber)+无原生线程", "是", "平缓",
                    "9.5", "Wren CLI、嵌入宿主、TIC-80", "3+", "低"
            },
            {
                    "Squirrel", "2003", "Alberto Demichelis", "脚本、面向对象、函数式",
                    "动态弱类型", "自动GC", "解释(Squirrel VM)", "游戏脚本、嵌入、AI逻辑",
                    "N/A", "否", "MIT", ".nut",
                    "无", "协程+线程(弱支持)", "是", "平缓",
                    "9.5", "Left 4 Dead、Portal 2、CS:GO、OpenTTD", "3+", "低"
            },
            {
                    "AngelScript", "2003", "Andreas Jönsson", "脚本、面向对象、泛型",
                    "静态强类型(可选)", "自动GC(可选)", "编译(字节码)", "游戏脚本、应用扩展、MOD系统",
                    "N/A", "否", "zlib", ".as",
                    "无", "无内置并发", "是", "平缓",
                    "9.5", "Amnesia、Overgrowth、Urho3D、Rigs of Rods", "2+", "低"
            },
            {
                    "Boo", "2003", "Rodrigo B. de Oliveira", "多范式(OOP、函数式、静态/动态)",
                    "静态强类型(可选鸭子类型)", "自动GC(CLR)", "编译(CLR字节码)", "脚本、Unity历史支持、CLI工具",
                    "N/A", "否", "BSD", ".boo",
                    "无", "线程+async", "是", "中等",
                    "9.0", "Unity(历史)、Boo Lang、BooKit", "2+", "极低(已废弃)"
            },
            {
                    "UnityScript", "2004", "Unity Technologies", "脚本、面向对象、ECMAScript风格",
                    "动态弱类型", "自动GC(CLR)", "编译(CLR字节码)", "Unity游戏脚本(2017前已废弃)",
                    "N/A", "否", "专有", ".js(Unity)",
                    "无", "协程+线程", "是", "平缓",
                    "N/A", "Unity(2017前)", "1", "无(已废弃)"
            },
            {
                    "HLSL", "2002", "Microsoft", "声明式、数据并行、过程式",
                    "静态强类型(C风格)", "手动(寄存器/常量缓冲区)", "编译(fxc/dxc)", "GPU着色、DirectX图形渲染、计算",
                    "N/A", "否", "专有", ".hlsl/.fx/.hlsli",
                    "无", "大规模并行(SIMD)", "是(DirectX平台)", "陡峭",
                    "11.0", "DirectX、Unity、Unreal Engine着色管线", "3+", "高"
            },
            {
                    "GLSL", "2003", "OpenGL ARB / Khronos", "声明式、数据并行、C风格",
                    "静态强类型", "手动(无堆内存)", "编译(驱动内置)", "GPU着色、OpenGL/WebGL/Vulkan图形",
                    "N/A", "否", "多种", ".glsl/.vert/.frag/.geom",
                    "无", "大规模并行(SIMD)", "是(OpenGL/Vulkan)", "陡峭",
                    "11.0", "OpenGL、WebGL、Vulkan(通过SPIR-V)、Three.js", "10+", "高"
            },
            {
                    "WGSL", "2021", "W3C / WebGPU社区", "声明式、数据并行、Rust风格",
                    "静态强类型", "手动(无堆内存)", "编译(Tint/Naga)", "WebGPU着色、跨平台GPU计算、Web图形",
                    "N/A", "否", "W3C/BSD", ".wgsl",
                    "无", "大规模并行(workgroup)", "是(WebGPU)", "陡峭",
                    "11.5", "Dawn、wgpu、Three.js(WebGPU)、Babylon.js", "3+", "中高"
            },
            {
                    "Metal Shading Language", "2014", "Apple", "声明式、数据并行、C++14子集",
                    "静态强类型", "手动", "编译(Metal编译器)", "Apple GPU计算、Metal图形、Core ML加速",
                    "N/A", "否", "专有", ".metal",
                    "无", "大规模并行(threadgroup)", "否(Apple生态)", "陡峭",
                    "12.0", "Metal框架、Core ML、Unity(Apple平台)", "2+", "中"
            },
            {
                    "SPIR-V", "2014", "Khronos Group", "中间表示、低级、并行",
                    "静态类型(无高级类型)", "手动", "二进制中间码", "Vulkan/OpenCL着色器中间码、跨API标准",
                    "N/A", "否", "多种", ".spv",
                    "无", "SIMD并行", "是(跨API)", "极陡峭",
                    "11.5", "Vulkan、OpenCL、WebGPU、MLIR", "5+", "高"
            },
            // === 区块链与智能合约 ===
            {
                    "Solidity", "2014", "Gavin Wood等 / Ethereum", "面向对象、合约、事件驱动",
                    "静态强类型", "自动(EVM内存管理)", "编译(EVM字节码)", "智能合约、DApp、DeFi、NFT",
                    "N/A", "否", "GPL-3.0", ".sol",
                    "npm/Hardhat/Foundry", "无(EVM单线程)", "是(EVM兼容链)", "中等",
                    "14.0", "OpenZeppelin、Hardhat、Foundry、Truffle、Ethers.js", "5+", "高"
            },
            {
                    "Vyper", "2017", "Vitalik Buterin等 / Ethereum", "合约、过程式、安全优先",
                    "静态强类型", "自动(EVM)", "编译(EVM字节码)", "智能合约(安全审计友好)、DeFi",
                    "N/A", "否", "Apache-2.0", ".vy",
                    "pip", "无(EVM单线程)", "是", "中等",
                    "14.5", "Curve Finance、安全合约模板", "2+", "中"
            },
            {
                    "Move", "2019", "Facebook(Libra/Diem) -> Aptos/Sui", "面向资源、安全、验证",
                    "静态强类型", "自动(线性类型)", "编译(字节码)", "区块链(Aptos/Sui)、智能合约、数字资产",
                    "N/A", "否", "Apache-2.0", ".move",
                    "Aptos CLI/Sui CLI", "无(并行执行引擎)", "是", "陡峭",
                    "15.0", "Aptos、Sui、Starcoin、Dojo引擎", "3+", "中高"
            },
            {
                    "Cairo", "2020", "StarkWare", "过程式、零知识证明、低级",
                    "静态强类型", "手动(Cairo VM)", "编译(CASM)", "ZK-Rollup、StarkNet、可验证计算",
                    "N/A", "否", "Apache-2.0", ".cairo",
                    "Scarb", "无(证明系统)", "是", "陡峭",
                    "15.0", "StarkNet、StarkEx、Dojo引擎、Herodotus", "2+", "中"
            },
            // === 硬件描述与验证 ===
            {
                    "Verilog", "1984", "Gateway Design Automation", "硬件描述、并发、事件驱动",
                    "静态类型(4态逻辑)", "手动(综合后硬件)", "编译(综合/仿真)", "数字电路设计、FPGA、ASIC验证",
                    "N/A", "是(IEEE 1364)", "专有/开源", ".v",
                    "无", "天然并发(always块)", "是", "陡峭",
                    "12.0", "Icarus Verilog、Vivado、ModelSim、Quartus", "10+", "中"
            },
            {
                    "VHDL", "1980", "美国国防部", "硬件描述、并发、强类型",
                    "静态强类型", "手动", "编译(综合/仿真)", "数字电路、FPGA、ASIC、航空航天",
                    "N/A", "是(IEEE 1076)", "专有/开源", ".vhd/.vhdl",
                    "无", "天然并发(process)", "是", "极陡峭",
                    "12.0", "GHDL、Vivado、ModelSim、Quartus", "10+", "中"
            },
            {
                    "SystemVerilog", "2005", "Accellera / IEEE", "硬件描述、验证、OOP",
                    "静态强类型", "手动", "编译(综合/仿真)", "芯片设计、验证、FPGA、SoC",
                    "N/A", "是(IEEE 1800)", "专有/开源", ".sv",
                    "无", "并发(多线程仿真)", "是", "极陡峭",
                    "13.0", "UVM、Vivado、Synopsys VCS、Cadence Xcelium", "8+", "高"
            },
            // === 数据库与查询语言 ===
            {
                    "T-SQL", "1989", "Microsoft / Sybase", "声明式、过程式扩展",
                    "静态类型(表结构)", "自动(SQL Server)", "解释+优化", "SQL Server数据库、企业BI、Azure",
                    "N/A", "否(ISO SQL扩展)", "专有", ".sql",
                    "无", "无(数据库引擎调度)", "是(Windows/Linux)", "中等",
                    "10.5", "SQL Server、SSIS、SSAS、Azure SQL、Power BI", "3+", "高"
            },
            {
                    "PL/SQL", "1990s", "Oracle", "过程式扩展SQL",
                    "静态类型", "自动(Oracle)", "编译(字节码)", "Oracle数据库、企业ERP、金融系统",
                    "N/A", "否(Oracle专有)", "专有", ".sql/.pls/.pck",
                    "无", "无(Oracle调度)", "是(跨平台)", "中等",
                    "11.0", "Oracle DB、Oracle Forms、EBS、PeopleSoft", "2+", "高"
            },
            {
                    "GraphQL", "2015", "Facebook / Lee Byron", "查询、声明式、图遍历",
                    "类型系统(Schema定义)", "自动(服务端)", "解释+执行", "API查询、前后端数据交互、BFF层",
                    "N/A", "否", "OWFa", ".graphql/.gql",
                    "npm", "无(查询级并行)", "是", "平缓",
                    "12.0", "Apollo、Relay、Hasura、Prisma、GraphQL Yoga", "10+", "极高"
            },
            // === 企业级与遗留系统 ===
            {
                    "ABAP", "1983", "SAP", "过程式、OOP、事件驱动",
                    "静态强类型", "自动(SAP GC)", "编译(字节码)", "SAP ERP、企业应用、S/4HANA",
                    "N/A", "否(SAP专有)", "专有", ".abap",
                    "无", "无(SAP应用服务器)", "是(SAP平台)", "陡峭",
                    "10.0", "SAP R/3、S/4HANA、SAP Fiori、SAP BTP", "2+", "中"
            },
            // === 教育、可视化与领域特定 ===
            {
                    "Scratch", "2007", "Mitchel Resnick / MIT Media Lab", "可视化、事件驱动、OOP",
                    "动态类型(块拼接)", "自动", "解释(Scratch VM)", "教育、儿童编程、计算思维入门",
                    "N/A", "否", "BSD-3", ".sb3/.sb2",
                    "无", "消息广播(伪并发)", "是(Web/桌面)", "极平缓",
                    "8.0", "Scratch在线编辑器、ScratchJr、Scratch Desktop", "3+", "高"
            },
            {
                    "Wolfram Language", "1988", "Stephen Wolfram / Wolfram Research", "多范式(符号、函数式、规则、模式匹配)",
                    "动态类型(符号)", "自动", "解释+编译", "科学计算、数学、知识引擎、AI、符号推理",
                    "N/A", "否", "专有", ".wl/.m",
                    "Paclet", "并行计算+GPU+分布式", "是(Wolfram Engine)", "陡峭",
                    "12.0", "Mathematica、Wolfram Alpha、System Modeler、Wolfram Cloud", "3+", "中"
            },
            {
                    "LabVIEW G", "1986", "National Instruments", "图形化、数据流、并发",
                    "静态强类型(连线类型)", "自动", "编译(图形转机器码)", "测试测量、工业控制、嵌入式、HIL仿真",
                    "N/A", "否", "专有", ".vi/.lvproj",
                    "NIPM", "天然数据流并发", "是(Windows/Linux/RTOS)", "中等",
                    "10.5", "NI DAQ、CompactRIO、PXI、TestStand、VeriStand", "2+", "中"
            },
            // === 脚本、工具与古老语言 ===
            {
                    "AWK", "1977", "Aho/Weinberger/Kernighan / Bell Labs", "脚本、文本处理、模式匹配",
                    "动态类型(字符串/数字自动转换)", "自动", "解释(gawk/mawk/awk)", "文本处理、日志分析、报表生成、管道",
                    "N/A", "否(POSIX标准)", "GPL", ".awk",
                    "无", "无(单线程)", "是(Unix/Linux)", "平缓",
                    "10.0", "gawk、mawk、nawk、awk", "5+", "中"
            },
            {
                    "Tcl", "1988", "John Ousterhout / UC Berkeley", "脚本、命令式、事件驱动",
                    "动态类型(一切皆字符串)", "自动", "解释(Tclsh)", "测试自动化、GUI(Tk)、EDA工具、网络协议",
                    "N/A", "否", "BSD", ".tcl",
                    "Tcllib/Teacup", "事件循环+线程", "是", "平缓",
                    "10.5", "Tk、Expect、SQLite(Tcl绑定)、EDA工具(Synopsys/Cadence)", "5+", "低"
            },
            {
                    "Forth", "1970", "Charles Moore", "堆栈式、命令式、扩展性",
                    "无类型(堆栈操作)", "手动", "编译/解释", "嵌入式、天文望远镜、启动加载、工业控制",
                    "N/A", "否(ANS Forth 1994)", "公有领域", ".f/.forth",
                    "无", "无(协程/任务字)", "是", "陡峭",
                    "10.0", "Gforth、Open Firmware、FreeBSD启动加载、JWST控制", "20+", "极低"
            },
            {
                    "PostScript", "1982", "Adobe Systems", "堆栈式、图灵完备、页面描述",
                    "动态类型", "自动", "解释(打印机/RIP)", "页面描述、打印、矢量图形、字体渲染",
                    "N/A", "是(Adobe/ISO)", "专有", ".ps",
                    "无", "无", "是(打印机/查看器)", "陡峭",
                    "9.5", "Adobe Acrobat、Ghostscript、打印机固件、RIP", "5+", "低"
            },
            {
                    "APL", "1966", "Kenneth Iverson / IBM", "数组、函数式、符号计算",
                    "动态类型(数组)", "自动", "解释+编译", "金融、量化交易、数据分析、学术研究",
                    "N/A", "是(ISO 8485)", "专有/开源", ".apl",
                    "无", "隐式并行(现代)", "是", "极陡峭",
                    "11.0", "Dyalog APL、GNU APL、J(衍生)、K(衍生)、Q(衍生)", "5+", "极低"
            },
            // === 函数式前沿与转译语言 ===
            {
                    "Idris", "2011", "Edwin Brady / 圣安德鲁斯大学", "纯函数式、依赖类型、证明",
                    "静态强类型(依赖类型)", "自动(GC)", "编译(C/JS/LLVM)", "形式验证、研究、安全系统、定理证明",
                    "N/A", "否", "BSD", ".idr",
                    "Idris包管理", "无(纯函数式)", "是", "极陡峭",
                    "12.0", "Idris 2、Blodwen、形式验证项目、Type Theory研究", "2+", "极低"
            },
            {
                    "Elm", "2012", "Evan Czaplicki", "函数式、响应式、前端",
                    "静态强类型(类型推断)", "自动", "编译(JavaScript)", "Web前端、SPA、无运行时错误保证",
                    "N/A", "否", "BSD-3", ".elm",
                    "elm-package/elm-json", "无(Elm架构消息循环)", "是(浏览器)", "中等",
                    "11.0", "Elm架构、elm-ui、elm-graphql、elm-spa", "2+", "中低"
            },
            {
                    "CoffeeScript", "2009", "Jeremy Ashkenas", "脚本、函数式、OOP",
                    "动态类型(转译JS)", "自动(JS GC)", "编译转译(为JS)", "Web前端、Node.js(历史项目)",
                    "N/A", "否", "MIT", ".coffee",
                    "npm", "同JavaScript", "是", "平缓",
                    "10.0", "Backbone.js(历史)、Atom编辑器(历史)、GitHub前端(历史)", "3+", "极低(已式微)"
            },
            // === Esoteric 与极限测试 ===
            {
                    "Brainfuck", "1993", "Urban Müller", "图灵完备、极简、指令式",
                    "无类型(8位字节单元)", "手动(30K数组)", "解释/编译", "教育、挑战、极客娱乐、编译器测试",
                    "N/A", "否", "公有领域", ".bf/.b",
                    "无", "无(单线程)", "是", "极陡峭(反直觉)",
                    "0.0", "无(纯语言挑战)、esolangs.org", "50+", "极低(趣味)"
            }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tileLayout = new TileLayout(this);
        setContentView(tileLayout, new ViewGroup.LayoutParams(-1, -1));
        int padding = dp2px(40);
        tileLayout.setPadding(padding, padding, padding, padding);
        tileLayout.setDebugMode(isDebugMode());
        tileLayout.setAdapter((adapter = new DataAdapter()));
        tileLayout.setDimenProvider((dimenProvider = new MeasurableDimenProvider(
                tileLayout.getDefaultTileWidth(),
                tileLayout.getDefaultTileHeight(),
                adapter
        )));
        // 空间换时间
        // dimenProvider.setWidths(new IntIntMapHashMap());
        // dimenProvider.setHeights(new IntIntMapHashMap());
        
        // 全测量(不建议大数据场景)
        dimenProvider.full();
        
        dragResizerView = new DragResizerView(this);
        dragResizerView.setCallback(new DragResizerView.Callback() {
            @Override
            public void onDrag(int direction, int width, int height, int gravity) {
                int column = TileCoreService.getColumn(currTileHolder);
                int row = TileCoreService.getRow(currTileHolder);
                tileLayout.setTileSize(
                    column, Math.max(tileLayout.getDefaultTileWidth(), width), gravity,
                    row, Math.max(tileLayout.getDefaultTileHeight(), height), gravity
                );
            }

            @Override
            public int getTileWidth() {
                return tileLayout.getTileWidth(TileCoreService.getColumn(currTileHolder));
            }

            @Override
            public int getTileHeight() {
                return tileLayout.getTileHeight(TileCoreService.getRow(currTileHolder));
            }
        });
    }

    @Override
    protected void onDebugModeChanged(boolean enabled) {
        tileLayout.setDebugMode(enabled);
    }

    @Override
    protected RandomSize onInitRandomSize() {
        return new RandomSize() {
            @Override
            public int getCenterColumn() {
                return tileLayout.findColumn(tileLayout.getWidth() / 2f);
            }
            @Override
            public int getCenterRow() {
                return tileLayout.findRow(tileLayout.getHeight() / 2f);
            }
            @Override
            public int getTileWidth(int column) {
                return tileLayout.getTileWidth(column);
            }
            @Override
            public int getTileHeight(int row) {
                return tileLayout.getTileHeight(row);
            }
            @Override
            public void setTileWidth(int column, int width) {
                tileLayout.setTileWidth(column, width, TileView.DIMEN_GRAVITY_CENTER);
            }
            @Override
            public void setTileHeight(int row, int height) {
                tileLayout.setTileHeight(row, height, TileView.DIMEN_GRAVITY_CENTER);
            }
        };
    }

    @Override
    public boolean hasMaxMode() {
        return false;
    }

    @Override
    public boolean hasPlanMode() {
        return false;
    }

    private class TextTileHolder extends TileLayout.TileHolder implements Measurable {

        private final TextView textView;

        public TextTileHolder(boolean isHeader, boolean isOddRow) {
            super(new FrameLayout(TableActivity.this));
            itemView.setLayoutParams(new ViewGroup.LayoutParams(-2, -2));
            textView = new TextView(TableActivity.this);
            ((FrameLayout) itemView).addView(textView, -1, -1);
            GradientDrawable gd = new GradientDrawable();
            gd.setStroke((int) Math.ceil(dpTopx(0.5f)), Color.GRAY);
            boolean isDarkMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            if (isHeader) {
                gd.setColor(getColorByAttr(android.R.attr.colorPrimary));
            } else {
                // 交替行背景色
                if (isDarkMode) {
                    // 暗色模式：深灰交替，和窗口背景接近
                    gd.setColor(isOddRow ? 0xFF1A1A1A : 0xFF212121);
                } else {
                    // 亮色模式：浅灰交替，和窗口背景接近
                    gd.setColor(isOddRow ? 0xFFF1F1F1 : 0xFFFFFFFF);
                }
            }
            TypedValue value = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
            textView.setBackground(new LayerDrawable(new Drawable[]{gd, ContextCompat.getDrawable(TableActivity.this, value.resourceId)}));
            textView.setTextColor(isHeader ? (isDarkMode ? 0xff000000 : 0xffffffff) : getColorByAttr(android.R.attr.textColorPrimary));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setGravity(Gravity.CENTER);
            textView.setSingleLine();
            int paddingHorizontal = dp2px(12);
            int paddingVertical = dp2px(8);
            textView.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);

            textView.setOnClickListener(v -> {
                long id = TileCoreService.getTileId(getColumn(), getRow());
                if (currTileHolder == id && isSelected) {
                    ((FrameLayout) itemView).removeView(dragResizerView);
                    isSelected = false;
                } else {
                    if (isSelected) {
                        TextTileHolder holder = (TextTileHolder) tileLayout.getActiveTile(TileCoreService.getColumn(currTileHolder), TileCoreService.getRow(currTileHolder));
                        if (holder != null) {
                            ((FrameLayout) holder.itemView).removeView(dragResizerView);
                        }
                    }
                    ((FrameLayout) itemView).addView(dragResizerView);
                    isSelected = true;
                    currTileHolder = id;
                }
            });
        }

        @Override
        public void onInWindow() {
            super.onInWindow();
            long id = TileCoreService.getTileId(getColumn(), getRow());
            if (isSelected && currTileHolder == id) {
                ((FrameLayout) itemView).addView(dragResizerView);
            }
        }

        @Override
        public void onOutWindow() {
            super.onOutWindow();
            long id = TileCoreService.getTileId(getColumn(), getRow());
            if (isSelected && currTileHolder == id) {
                ((FrameLayout) itemView).removeView(dragResizerView);
            }
        }

        private int getColorByAttr(int resId) {
            TypedValue value = new TypedValue();
            getTheme().resolveAttribute(resId, value, true);
            return ContextCompat.getColor(TableActivity.this, value.resourceId);
        }

        @Override
        public void measure(int widthMeasureSpec, int heightMeasureSpec, int[] out) {
            textView.measure(widthMeasureSpec, heightMeasureSpec);
            out[0] = textView.getMeasuredWidth();
            out[1] = textView.getMeasuredHeight();
        }
    }

    private class DataAdapter extends TileLayout.Adapter {
        @Override
        public int getLeftBound() {
            return 0;
        }

        @Override
        public int getTopBound() {
            return 0;
        }

        @Override
        public int getRightBound() {
            return data[0].length - 1;
        }

        @Override
        public int getBottomBound() {
            return data.length - 1;
        }

        @Override
        public TileLayout.TileHolder onCreateTileHolder(int type) {
            return new TextTileHolder(type == 0, type == 1);
        }

        @Override
        public int getTileType(int column, int row) {
            return row == 0 ? 0 : (row % 2 == 0 ? 1 : 2);
        }

        @Override
        public void onBindTileHolder(TileLayout.TileHolder holder, int column, int row) {
            TextTileHolder textTileHolder = (TextTileHolder) holder;
            textTileHolder.textView.setText(data[row][column]);
            textTileHolder.textView.setOnLongClickListener(v -> {
                showToast(data[row][column]);
                return true;
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dimenProvider != null) {
            dimenProvider.clearRecycledTiles();
        }
        tileLayout.setAdapter(null);
    }


}
