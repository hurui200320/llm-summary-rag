# llm-summary-rag

## Definition of the issue

I was trying to feed a bunch of books (more specifically, all 24 volumes of the Spice and Wolf series)
into a large language model (LLM). I would like to have it read all the content, then I can chat with it
to discuss various details in the story.

However, with normal RAG, the model can only get pieces of the original content based on your input prompt.
And overall, considering the span of the stories, normal RAG is not working well under this scenario.

I also tried agentic RAG, where the model can generate its own query and use it to fetch the relevant content.
However, agentic RAG relies on the base model's knowledge to generate such queries.

Shortly speaking:

> LLM's knowledge = info stored in weights + info provided in the context
> 
> The content in RAG is not in either of the two categories, thus, they are totally unknown to the LLM.
> Just like any other human, you can't ask about things you don't know you don't know.

This project is a side project for myself to get some fun, by trying to solve this issue on Java.

I know there are a bunch of possible solutions on Python, for example, GraphRAG from Microsoft,
or even Python's langchain library has some built-in solution to ease the issue.
But no, thanks. I don't like Python.

## Organization of the trials

Unlike my other projects, this project is more like a test field.
Thus, each branch represents a different trial.
The master branch is the most promising one which I'm continuing moving forward.
All other branches are just failed attempts.

## Trial: hard cut with context summarization

Maybe we can do hard cut slicing but skip the chunking part and directly ask AI to summarize?

For example, each chunk is about 250 tokens (use embedding token estimator to decide). Then:

`A | BC | D`, A and D are solely present for context, so AI can know what happens even if we cut
at the middle of the sentence. Then we ask AI to summarize the `BC` chunk, which is about 500 tokens in total.

When putting into RAG, we record those three parts in metadata, but only calculate `BC` for the vector.

Next turn we do: `C | DE | F`, ...

Initially we can do: ` (empty) | AB | C`, and at the end we do ` X | YZ | (empty)`.

In practice, we can use something like PEM:

```
-----BEGIN PREVIOUS BLOCK-----
Content of A here...
-----END PREVIOUS BLOCK-----
-----BEGIN CURRENT BLOCK-----
Content of B and C here...
-----END CURRENT BLOCK-----
-----BEGIN NEXT BLOCK-----
Content of D here...
-----END NEXT BLOCK-----
```

Test using Gemini 3 flash works, but a lot of content has been blocked due to PROHIBITED CONTENT. In total, 14 out of 101 chunks failed to summary.

Rollback to Gemini 2.5 flash. 9 out of 101 chunks failed to summary, but the quality of the summary is significantly worse.

Trying Gemini 3 pro，11 out of 101 chunks failed to summary. The quality has not improved much.

Tried GPT-5.1, which is all good. The GPT 5.2 improves tool calling and agentic tool,
which is not useful here, so just using 5.1 to save some money.

For RAG, since the raw texts are hard cut, so there is no point to do RAG.
However, we do want to do RAG for both chunk summary and book summary,
providing tools to search RAG and return both matched books and chunks.
Also providing search text using something like Apache Lucene.

Re rank? No, don't over optimize.

For Lucene:
  + SmartChineseAnalyser looks promissing, but can't handle simplified and traditional Chinese
  + Replace with ICU tokenizer with N gram tokenizer

Tools:
+ List docs (id, title, author, summary, lang)
+ List chunks (docId) - chunk index, chunk summary
+ Search doc summaries RAG - return matched docs id and their summary
+ Search chunk summaries RAG - return matched (docId, chunkIndex) and their summary
+ Search text (any or all) - return matched (docId, chunkIndex)
+ Read chunk (docId, chunk Index)

Tweaked the parameter:
+ Considering each summary will be a RAG vector, thus setting the chunk summary to 500 chars.
+ The book summary is still 1000 chars.
+ To support the 500 chars summary, each chunk is now 1500 chars, with 500 chars of pre content and 500 chars of post content.

After this tweaking, the summary makes more sense and looks more meaningful.

General steps on ingesting a book:
1. create an entry in the database, in the document table
2. Slice the book into chunks
3. Generate summary for each chunk
4. Generate book summary
5. Generate RAG vectors for each chunk summary and book summary
6. Build lucene index for searching

## MCP Server

To not reinvent a chat frontend or something similar (not mention I'm not good at frontend and Java is not good at TUI), I decided to provide a MCP server for the tools I implemented.

The MCP server only supports streamable http, which is the latest recommended way of doing MCP. Tested with both OpenCode and llama.cpp's built-in web ui, it performs well. Here is an example using google-gemma-4-31b-q5km-text (256K context, no vision):

System prompt:
```
# Role
你是一个读书助手，能够根据用户提供的问题，从数据库中检索相关文档，并提供答案。
请务必通过调用工具来查询书籍或文档的内容，不要凭空捏造答案。
在回答用户问题时，你可以利用自身的知识和推理能力，但需要通过工具确认这是文档中存在的，而非你的幻觉。

# Tools
本系统提供了一系列工具以便你在回答问题时查询已录入文档的内容。这些工具没有调用限制，你可随意使用。
数据库中的文档被切分成固定长度的 Chunk，每个 chunk 具有在文档内的编号（index，从 1 开始），
并且每个 chunk 都有一个摘要，用于在不阅读较长原文的情况下快速了解该 chunk 的内容。
同时基于所有 chunk 的摘要，每个文档也有一个摘要，用于简要了解文档的内容。
系统配备了基于 Apache Lucene 的全文关键词检索工具（all match 和 any match），
同时针对摘要也配备了基于嵌入向量相似度的检索工具。

通常情况下建议你首先列出所有文档的元数据，这会包含文档标题等简短信息。
随后根据用户的提问和你的分析，你可以查阅特定文档的摘要以便更详细的了解文档内容。
最后根据需求使用关键词或 RAG 检索特定文档或 chunk，并按需调用对应的原文。
```

Query:
> 在终将成为你的小说第三卷中，再与枝元阳挥手告别后，佐伯沙弥香内心想到"我像是被绳索牵引一般，回想起差点要忘记的老面孔"，文中没有给出具体的答案，而下句则说"在课堂开始前的短暂时间，我这样想着枝元学妹与过去的事情。至少我不至于忘记她的名字和长相"。她说想着过去的事情，至少不至于忘记名字和长相，我想不到一个确切地答案，我不知道此时此刻沙弥香想到了谁。我觉得可能是或者小学时期游泳班上遇到的女孩，或者是七海灯子，但我不确定（但我能确定应该不是柚木学姐）。我在犹豫会不会是游泳班的女孩，因为枝元阳的行为很像这个女孩，但是第一卷提及女孩的时候一直没有给出名字，只是从始至终用"女孩"来指代这个角色。

Model's tool call:

1. list_documents: list all documents in database, response: all three volumes of the book
2. search_kw_all, "绳索牵引", "老面孔", response: provide the chunk I quoted in the query
3. search_kw_any, "游泳", "七海燈子", response: provided chunks from volume 2 related to 七海燈子
4. search_kw_any, "游泳班", "小學", response: provided chunks from volume 1 related to the girl from swimming class

Final response:

> 根据对小说文本的检索和分析，我可以确认你的直觉是正确的。佐伯沙弥香在第三卷中提到的那个“差点要忘记的老面孔”，指的正是小学时期游泳班上遇到的那个女孩。
> 
> 以下是详细的推导过程和证据：
> 
> 1. 性格上的共鸣（触发点）
> 在第三卷的这段话之前，沙弥香正在思考枝元阳的性格：“举止甚至不只是活泼好动，更像是情感过于丰富。我过去从未跟这种个性的人相处过。”
> 紧接着她就想到了那个“老面孔”。这说明这个被想起的人具有与枝元阳类似的活泼、热情、不受拘束且情感外露的特质，而这些特质恰恰是沙弥香在成长过程中很少接触并感到有些不习惯的。
> 
> 2. 对比与线索（第一卷的回忆）
> 在第一卷中，小说详细描述了沙弥香小学时的游泳班经历：
> 
> + 女孩的形象：那个女孩皮肤晒得黝黑，性格极其随性、不正经，经常用一种很自然的方式与沙弥香“装熟”。
> + 行为模式：在所有人都游蛙式时，她一个人游自由式且速度极快，让追求完美和名列前茅的沙弥香产生了挫败感。
> + 性格差异：沙弥香明确提到“我跟她的个性天差地远”，这种反差与她在第三卷描述枝元阳时的感受完全一致。
> 
> 3. 直接的验证（第三卷后续情节）
> 在第三卷的后续章节中，当沙弥香与枝元阳一起去游泳时，文中出现了一个非常明确的伏笔：
> 
> 当她看着泳池里皮肤晒黑、自在游水的枝元阳时，产生了一种强烈的既视感（Déjà vu）。
> 文中直接写道：“那个时候，我也输了。” 这句话直接将当下的场景与小学游泳班被那个女孩反超的记忆重叠在一起。
> 
> 结论
> 
> 虽然文中确实始终没有给这个女孩起名字，仅用“女孩”代称，但通过 **【活泼性格 → 游泳记忆 → 竞争挫败感】** 这条逻辑链条，可以确定她想到的就是那个小学时让她感到困惑且无法掌控的游泳班女孩。
> 
> 至于你犹豫的其他人选：
> 
> 七海灯子：沙弥香对灯子的感情更多是仰慕、将对方视为“完美”的竞争对手（在学习上），而非这种由于性格反差而产生的、带有某种本能牵引的旧日记忆。
> 柚木学姐：正如你所确定的，两者的特质完全不同。
> 所以，你的猜测是正确的，那个被绳索牵引般想起的人就是那个没有名字的游泳班女孩。
