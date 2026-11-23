$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$bitmap = [Drawing.Bitmap]::new(256, 256)
$graphics = [Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.Clear([Drawing.Color]::FromArgb(25,44,65))
$brush = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(75,181,201))
$graphics.FillRectangle($brush, 47, 50, 162, 151)
$pen = [Drawing.Pen]::new([Drawing.Color]::FromArgb(25,44,65), 12)
$graphics.DrawLine($pen, 47,94,209,94)
$graphics.DrawLine($pen, 128,50,128,114)
$pen.Dispose()
$pen = [Drawing.Pen]::new([Drawing.Color]::White, 15)
$pen.StartCap = [Drawing.Drawing2D.LineCap]::Round
$pen.EndCap = [Drawing.Drawing2D.LineCap]::Round
$graphics.DrawLines($pen, [Drawing.Point[]]@([Drawing.Point]::new(91,151),[Drawing.Point]::new(117,177),[Drawing.Point]::new(168,126)))
$stream = [IO.MemoryStream]::new()
$bitmap.Save($stream,[Drawing.Imaging.ImageFormat]::Png)
$png = $stream.ToArray()
$output = [IO.File]::Create((Join-Path $PSScriptRoot 'autocheck.ico'))
$writer = [IO.BinaryWriter]::new($output)
try {
    $writer.Write([uint16]0); $writer.Write([uint16]1); $writer.Write([uint16]1)
    $writer.Write([byte]0); $writer.Write([byte]0); $writer.Write([byte]0); $writer.Write([byte]0)
    $writer.Write([uint16]1); $writer.Write([uint16]32)
    $writer.Write([uint32]$png.Length); $writer.Write([uint32]22); $writer.Write($png)
} finally { $writer.Dispose(); $stream.Dispose(); $pen.Dispose(); $brush.Dispose(); $graphics.Dispose(); $bitmap.Dispose() }
