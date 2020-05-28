#------------------------------------------------------------------------------
# uTorrent
#   Shows path where uTorrent client installed (default is C:\Users\<user>\AppData\Roaming\uTorrent)
#   Version of uTorrent client installed
#   Computer ID (should match 'cids' entry in settings.dat)
#
# Change history
#   20180615 - first release
#
# References
#   n/a
#
# Copyright
#   Michael Godfrey (c) 2018 
#   mgodfrey [at] gmail.com
#
#------------------------------------------------------------------------------

package utorrent;
use strict;

my %config =
(
  hive          => "NTUSER\.DAT",
  hasShortDescr => 0,
  hasDescr      => 1,
  hasRefs       => 1,
  osmask        => 29,
  version       => 20180615
);

sub getConfig     {return %config;}
sub getDescr      {return "Shows uTorrent client install path, version and Unique ID of computer";}
sub getRefs       {return "n/a";}
sub getHive       {return $config{hive};}
sub getVersion    {return $config{version};}

my $VERSION = getVersion();

sub pluginmain
{
  my $class = shift;
  my $hive = shift;
     ::logMsg('Launching uTorrent v'.$VERSION);
     ::rptMsg('utorrent v'.$VERSION.' ('.getDescr().")");
  my $reg = Parse::Win32Registry->new($hive);
  my $root_key = $reg->get_root_key;
     enum_recursively ($root_key, "Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\uTorrent", 1,"");
     enum_recursively ($root_key, "Software\\BitTorrent", 1,"");
}

sub hexify
{
my $data = shift;
my $l='';
my $r='';
my $n=0;
my $nd='';
for (my $i=0; $i<length($data); $i++)

{
  my $c = substr($data, $i, 1);
  $l.=sprintf("%02X ",ord($c));
  if ($c=~ m/[ -~]/) {$r.=$c;}else{$r.='.';}
  $n++;
  if ($n>15)

  {
    $nd.=sprintf("%-48s%s\n", $l,$r);
    $l='';$r='';$n=0;
  }
}
if ($n!=0)
  {
    $nd.=sprintf("%-48s%s\n", $l,$r);

  }
return $nd;
}

sub enum_recursively
{
my $root_key = shift;
my $key_path = shift;
my $rec_level = shift;
return if ($rec_level>3);
my $find = shift;$find = '.' if $find eq '';
my $key;
my $key_printed=0;
my $sep = ' ' x 2;

if ($key = $root_key->get_subkey($key_path))
{

  $sep = ' ' x 4;
  my @vals = $key->get_list_of_values();
  my %ac_vals;
  foreach my $v (sort {lc($a) <=> lc($b)} @vals)
  {
      my $vd = $v->get_data();
      my $vt = $v->get_type_as_string();
      if ($vt !~ /REG_(DWORD|SZ|EXPAND_SZ)/)
      {
       $vd = hexify($vd);
      }
      $ac_vals{$v->get_name()}{'VT'} = $vt;
      $ac_vals{$v->get_name()}{'VD'} = $vd;
  }
  foreach my $a (sort {lc($a) <=> lc($b)} keys %ac_vals)
  {
      my $ax = $a; $ax = '(Default)' if $a eq '';
      my $vt = $ac_vals{$a}{'VT'};
      my $vd = $ac_vals{$a}{'VD'};
      if (($a.$vd) ne ''&& ($ax.$a.$vd) =~/$find/is)
      {
          if ($key_printed==0)
          {
            ::rptMsg("\n");
            ::rptMsg($sep.$key_path);
            ::rptMsg($sep.'LastWrite Time '.gmtime($key->get_timestamp())." (UTC)\n");
          $key_printed=1;
          }
          $sep = ' ' x 4;
          ::rptMsg($sep.$ax);
          $sep = ' ' x 6;
          ::rptMsg($sep.$vt);
          $sep = ' ' x 8;
          if ($vt !~ /REG_(DWORD|SZ|EXPAND_SZ)/)
          {
           $vd =~ s/[\n]+/\n$sep/sg;
          }
          ::rptMsg($sep.$vd);
      }

  }
   my @subkeys = $key->get_list_of_subkeys();
   if (scalar(@subkeys) > 0)
   {
     foreach my $s (@subkeys)
     {
       enum_recursively ($root_key , $key_path."\\".$s->get_name(), $rec_level + 1,$find);
     }
   }
}
else
{
  ::rptMsg($sep.$key_path.' not found.');
}
}
