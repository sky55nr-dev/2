param([ValidateSet('shell','push')][string]$Action='shell',[string]$Command,[string]$LocalPath,[string]$RemotePath)
$ErrorActionPreference='Stop'
function Read-Exact($Stream,[int]$Length) {
    $buffer=New-Object byte[] $Length; $offset=0
    while($offset -lt $Length) { $n=$Stream.Read($buffer,$offset,$Length-$offset); if($n -eq 0){throw 'ADB connection closed'}; $offset += $n }
    return ,$buffer
}
function Request($Stream,[string]$Value) {
    $bytes=[Text.Encoding]::UTF8.GetBytes($Value)
    $prefix=[Text.Encoding]::ASCII.GetBytes(('{0:x4}' -f $bytes.Length))
    $Stream.Write($prefix,0,4); $Stream.Write($bytes,0,$bytes.Length)
    $status=[Text.Encoding]::ASCII.GetString((Read-Exact $Stream 4))
    if($status -ne 'OKAY') { $size=[Convert]::ToInt32([Text.Encoding]::ASCII.GetString((Read-Exact $Stream 4)),16); throw [Text.Encoding]::UTF8.GetString((Read-Exact $Stream $size)) }
}
function Sync-Packet($Stream,[string]$Id,[byte[]]$Data) {
    $idBytes=[Text.Encoding]::ASCII.GetBytes($Id); $length=[BitConverter]::GetBytes([int]$Data.Length)
    $Stream.Write($idBytes,0,4); $Stream.Write($length,0,4); $Stream.Write($Data,0,$Data.Length)
}
$client=[Net.Sockets.TcpClient]::new('127.0.0.1',5037)
$client.ReceiveTimeout=60000; $client.SendTimeout=60000
try {
    $stream=$client.GetStream(); Request $stream 'host:transport:emulator-5554'
    if($Action -eq 'shell') {
        Request $stream ('shell:'+$Command)
        $result=[IO.MemoryStream]::new(); $buffer=New-Object byte[] 65536
        while(($n=$stream.Read($buffer,0,$buffer.Length)) -gt 0){$result.Write($buffer,0,$n)}
        [Text.Encoding]::UTF8.GetString($result.ToArray())
    } else {
        Request $stream 'sync:'
        Sync-Packet $stream 'SEND' ([Text.Encoding]::UTF8.GetBytes($RemotePath+',33188'))
        $file=[IO.File]::OpenRead($LocalPath)
        try { $buffer=New-Object byte[] 65536; while(($n=$file.Read($buffer,0,$buffer.Length)) -gt 0) {
            $id=[Text.Encoding]::ASCII.GetBytes('DATA'); $len=[BitConverter]::GetBytes([int]$n)
            $stream.Write($id,0,4); $stream.Write($len,0,4); $stream.Write($buffer,0,$n)
        }} finally {$file.Dispose()}
        $done=[Text.Encoding]::ASCII.GetBytes('DONE'); $stamp=[BitConverter]::GetBytes([int][DateTimeOffset]::UtcNow.ToUnixTimeSeconds())
        $stream.Write($done,0,4); $stream.Write($stamp,0,4)
        $status=[Text.Encoding]::ASCII.GetString((Read-Exact $stream 4)); $length=[BitConverter]::ToInt32((Read-Exact $stream 4),0)
        if($status -ne 'OKAY'){throw [Text.Encoding]::UTF8.GetString((Read-Exact $stream $length))}
        'PUSH_OK'
    }
} finally { $client.Dispose() }