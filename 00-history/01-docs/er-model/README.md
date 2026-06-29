把「人工讀 code 整理出 data flow」這件事改成「跑一個 script 自動掃 code、產出 HTML」。                                                   
                                                                                                                                                                
     具體分三層理解：                                                                                                                                           
                                                                                                                                                                
     1. 「解析原始碼」是什麼                                                                                                                                    
                                                                                                                                                                
     不是 grep 文字，而是把 Java 檔案解析成 AST（Abstract Syntax Tree，語法樹），這樣可以精確問：                                                               
     - 哪些 class 有 @RestController？                                                                                                                          
     - 裡面哪些 method 有 @PostMapping("/v1/noauth/login")？                                                                                                    
     - 這個 method 呼叫了哪些其他 method？（call graph）                                                                                                        
     - 那些 method 裡面呼叫了哪個 XxxRepository.save(...) 或 findByEmail(...)？                                                                                 
     - 該 Repository 對應的 @Entity 是哪張表、哪些欄位？                                                                                                        
                                                                                                                                                                
     工具選擇：                                                                                                                                                 
     - Python：javalang（純 Python parser，輕量）或 tree-sitter-java                                                                                            
     - Java 原生：JavaParser（最準，但要寫 Java）                                                                                                               
     - 折衷：用 tree-sitter 抓 annotation + method 呼叫，夠用                                                                                                   
                                                                                                                                                                
     2. 跑起來會長什麼樣                                                                                                                                        
                                                                                                                                                                
     bash                                                                                                                                                       
     用法示意                                                                                                                                                   
     $ python tools/gen-api-dataflow.py --endpoint POST:/v1/noauth/login                                                                                        
     → 產出 01-docs/api-dataflow/login.html                                                                                                                     
     或一次全掃                                                                                                                                                 
     $ python tools/gen-api-dataflow.py --all                                                                                                                   
     → 掃出所有 @PostMapping / @GetMapping，每個 API 產一個 html                                                                                                
     → 再產一個 index.html 列表                                                                                                                                 
                                                                                                                                                                
     Script 內部流程：                                                                                                                                          
                                                                                                                                                                
     1. 找所有 Controller class，列出 endpoint → method 對應表                                                                                                  
     2. 對指定 endpoint 的 method，遞迴展開呼叫鏈（Controller → Service → Provider）                                                                            
     3. 在呼叫鏈裡掃出所有 *Repository.xxx() 呼叫                                                                                                               
     4. 對照 Entity 註解（@Table, @Column）解出 table 名 + 欄位                                                                                                 
     5. 區分 R/W：findBy/get → R；save/delete/update → W                                                                                                        
     6. 把這份結構化資料丟進 Jinja2 template，產 HTML（同你現在那個版面）                                                                                       
                                                                                                                                                                
     3. 「可以做到什麼程度」要誠實講                                                                                                                            
                                                                                                                                                                
     ✅ 靠 AST 能自動產出的部分                                                                                                                                 
     - API 端點清單（@PostMapping path）                                                                                                                        
     - 該 API 呼叫到哪些 service / repository                                                                                                                   
     - 涉及的 table 清單 + 欄位清單（從 Entity 反查）                                                                                                           
     - R/W 區分（依方法名 prefix）                                                                                                                              
     - Repository 方法的呼叫行號（深連到 GitHub / 本地）                                                                                                        
                                                                                                                                                                
     ⚠️ 半自動（要寫規則或標註）                                                                                                                                
     - alt 分支條件（if user.locked / if expired）— 要從 AST 的 if-statement 抽 condition 文字                                                                  
     - 欄位的「值變化」（loginFailCount++、locked=true）— 要解 assignment expression                                                                            
     - Redis / Cookie 副作用 — 要寫 pattern 認 RedisTemplate.opsForValue()、Cookie(...)                                                                         
                                                                                                                                                                
     ❌ 靠 AST 做不到、需要人補                                                                                                                                 
     - 「為什麼」的設計理由（例如為何 SUPER_ADMIN 不綁 tenant）                                                                                                 
     - 業務分支的命名（「強制改密碼分支」「多 tenant 分支」）                                                                                                   
     - Sequence diagram 的敘事順序（AST 給的是樹，不是時間線）                                                                                                  
                                                                                                                                                                
     所以實務上會做成 「八成自動 + 兩成手寫覆蓋」：                                                                                                             
                                                                                                                                                                
     docs-meta/api-dataflow/login.yaml   ← 人寫的補充（branch 命名、設計註）                                                                                    
            ↓ merge                                                                                                                                             
     gen-api-dataflow.py 解析 src        ← 自動部分（tables, fields, calls）                                                                                    
            ↓ render                                                                                                                                            
     01-docs/api-dataflow/login.html     ← 最終產出                                                                                                             
                                                                                                                                                                
     值不值得做？                                                                                                                                               
                                                                                                                                                                
     | 場景                                       | 值不值得                          |                                                                         
     |--------------------------------------------|-----------------------------------|                                                                         
     | 只想做 login 一份                          | 不值得，手寫快 10 倍              |                                                                         
     | 想做 20+ 個 API、而且 code 還在改          | 值得，code 改完重跑 script 就同步 |                                                                         
     | 想做 onboarding 文件 / 內外稽核資料        | 值得，可確保「文件 = code 現況」  |                                                                         
     | 想抓 N+1、漏寫 audit log、漏 tenant filter | 超值得，靜態分析還能加 lint rule  |                                                                         
                                                                                                                                                                
     我的建議：你這個 repo 看起來是長期維護的多 tenant 系統，值得做。但不要一次做完美，分三階段：                                                               
                                                                                                                                                                
     1. MVP：只抓 @PostMapping + Repository 呼叫，產一份「API → tables」對照表（單一 HTML，全部 API 一頁），約 200 行 Python                                    
     2. 加細節：每個 API 一頁，加上 R/W、欄位、行號連結                                                                                                         
     3. 加敘事：人工 yaml 覆蓋 sequence diagram、分支命名、欄位變化表  