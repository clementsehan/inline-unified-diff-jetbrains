// demo.js - Pseudo code demo for inline unified diff plugin

// ===== Data Structures =====

class DiffChunk {
  constructor(startLine, endLine, type, oldContent, newContent) {
    this.startLine = startLine
    this.endLine = endLine
    this.type = type           // "insert" | "delete" | "replace"
    this.oldContent = oldContent
    this.accepted = false
    this.rejected = false
  }
}

class DiffSession {
  constructor(filePath, chunks) {
    this.filePath = filePath
    this.chunks = chunks
    this.totalChunks = chunks.length
    this.resolvedCount = 0
  }

  isComplete() {
    return this.resolvedCount === this.totalChunks
  }
}

// ===== Diff Parser =====

function parseUnifiedDiff(rawDiff) {
  const lines = rawDiff.split("\n")
  const chunks = []
  let currentChunk = null

  for (const line of lines) {
    if (line.startsWith("@@")) {
      if (currentChunk) chunks.push(currentChunk)
      currentChunk = parseHunkHeader(line)
    } else if (line.startsWith("+")) {
      currentChunk.newContent.push(line.slice(1))
    } else if (line.startsWith("-")) {
      currentChunk.oldContent.push(line.slice(1))
    } else {
      currentChunk.contextLines.push(line.slice(1))
    }
  }

  if (currentChunk) chunks.push(currentChunk)
  return chunks
}

function parseHunkHeader(header) {
  // Extract @@ -startLine,count +startLine,count @@ from header
  const match = header.match(/@@ -(\d+),(\d+) \+(\d+),(\d+) @@/)
  return {
    oldStart: parseInt(match[1]),
    oldCount: parseInt(match[2]),
    newStart: parseInt(match[3]),
    newCount: parseInt(match[4]),
    oldContent: [],
    newContent: [],
    contextLines: []
  }
}

// ===== Renderer =====

function renderChunkInEditor(editor, chunk) {
  const startOffset = editor.lineToOffset(chunk.startLine)
  const endOffset = editor.lineToOffset(chunk.endLine)

  if (chunk.type === "insert") {
    renderInsertHighlight(editor, startOffset, endOffset)
  } else if (chunk.type === "delete") {
    renderDeleteHighlight(editor, startOffset, endOffset)
  } else {
    renderReplaceHighlight(editor, startOffset, endOffset)
  }

  attachAcceptRejectButtons(editor, chunk)
}

function attachAcceptRejectButtons(editor, chunk) {
  const gutter = editor.getGutter()
  const acceptBtn = createButton("Accept", () => acceptChunk(chunk))
  const rejectBtn = createButton("Reject", () => rejectChunk(chunk))
  gutter.addWidget(chunk.startLine, acceptBtn)
  gutter.addWidget(chunk.startLine, rejectBtn)
}

// ===== Actions =====

function acceptChunk(session, chunk) {
  applyChunkToDocument(chunk)
  removeHighlight(chunk)
  chunk.accepted = true
  session.resolvedCount++
  if (session.isComplete()) onSessionComplete(session)
}

function rejectChunk(session, chunk) {
  removeHighlight(chunk)
  chunk.rejected = true
  session.resolvedCount++
  if (session.isComplete()) onSessionComplete(session)
}

function acceptAll(session) {
  session.chunks.forEach(chunk => acceptChunk(session, chunk))
}

function rejectAll(session) {
  session.chunks.forEach(chunk => rejectChunk(session, chunk))
}

// ===== Session Lifecycle =====

function onSessionComplete(session) {
  hideSummaryPanel(session)
  notifyUser(`All ${session.totalChunks} diff chunks resolved in ${session.filePath}`)
}