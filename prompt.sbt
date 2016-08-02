
promptTheme := Scalapenos

triggeredMessage := { state =>
  "\u001B[2J\u001B[0\u003B0H" + solidLine(215, s"(${state.count}) Watching...")
}

watchingMessage := { _ => solidLine(215, "Press [Enter] to stop") }

def solidLine(colorCode: Int, text: String = "") = {
  val width = jline.TerminalFactory.get().getWidth()
  val left = " " * (width / 2 - text.size / 2)
  val right = " " * (width - left.size - text.size)

  bg(colorCode) + fg(colorCode) +
  left + fg(8) + text + fg(colorCode) + right +  // Print solid line
  "\u001b[49m"                                   // Reset colors
}

def bg(colorCode: Int) = {
  s"\u001b[48;5;${colorCode}m"
}

def fg(colorCode: Int) = {
  s"\u001b[38;5;${colorCode}m"
}
