#------------------------------------------------------------------------------ 
# cortana.pl
#  Acquires search terms from Cortana in Windows 10 
# 
# Change history 
#   20150627 - v1 
# 
# References 
#   Internal testing and verification using both manually typed search terms and verbal search terms 
#	using Cortana and the default search engine.  Other browsers were tested and yielded the same results. 
# 
# author: P. Seagren, patrick.seagren@outlook.com
#------------------------------------------------------------------------------ 

package cortana; 
use strict; 

my %config = 
( 
  hive          => "ntuser.dat", 
  hasShortDescr => 1, 
  hasDescr      => 1, 
  hasRefs       => 1, 
  osmask        => 22, 
  version       => 20150628 
); 

sub getConfig     {return %config;} 
sub getShortDescr {return "Search terms from Cortana/search bar";} 
sub getDescr      {return "Written and voice command search terms from the Cortana/search bar";} 
sub getRefs       {return "Internal testing and verification";} 
sub getHive       {return $config{hive};} 
sub getVersion    {return $config{version};} 

my $VERSION = getVersion(); 

sub pluginmain 
{ 
  my $class = shift; 
  my $hive = shift; 
     ::logMsg('Launching cortana v'.$VERSION); 
     ::rptMsg('cortana v'.$VERSION.' ('.getShortDescr().")"); 
  my $reg = Parse::Win32Registry->new($hive); 
  my $root_key = $reg->get_root_key; 
 #    my_enum ($root_key, "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts"); 
     enum_recursively ($root_key, "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts"); 
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
my $cortana_string=".com/search";
 
 
if ($key = $root_key->get_subkey($key_path)) 
{ 
  #::rptMsg("   inside if ..key=".$key->get_name());
 
  my  $key_name =  $key->get_name();
  if (($rec_level == 1) && (index($key_name, $cortana_string) != 0)){
     # ::rptMsg("   It does not contain CORTANA ");
	 return ;
  }
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
	  
	  # ::rptMsg("for each a=".$a);
	  # ::rptMsg("for each ax=".$ax);
	  # ::rptMsg("for each vt=".$vt);
	  # ::rptMsg("for each vd=".$vd);
	   
      if (($a.$vd) ne '' && ($ax.$a.$vd) =~/$find/is) 
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
	  
		#::rptMsg("   for each subkey=".@subkeys.",  s=".$s.", s-name=".$s->get_name());
		#::rptMsg("   for each rec_level=".$rec_level.",  find=".$find);
       enum_recursively ($root_key , $key_path."\\".$s->get_name(), $rec_level + 1,$find); 
     } 
   } 
} 
else 
{ 
  ::rptMsg($sep.$key_path.' not found.'); 
} 
} 