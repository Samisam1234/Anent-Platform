Add-Type -AssemblyName "System.IO.Compression.FileSystem"
Add-Type -AssemblyName "DocumentFormat.OpenXml"

# Create a simple DOCX file with the test resume content
$docxPath = "E:\AI Agent\agent-platform\Samiuddin_IT_B.Tech.docx"

# Use PowerShell to create a simple DOCX using Open XML SDK approach
# Since we don't have the OpenXML SDK easily available in PowerShell, let's use a different approach
# Create a simple RTF file and rename it, or use a different approach

# Actually, let's use the existing test infrastructure - there's a buildDocx method in the tests
# Let me create a simple PowerShell script that uses the .NET Open XML SDK if available

# For now, let's create a simple text file and use the existing test infrastructure
# The test uses buildDocx which uses Apache POI / Open XML SDK

# Let me check if there's a compiled test class we can use