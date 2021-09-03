<?php
ini_set('display_errors',1);
error_reporting(E_ALL);
echo "one\n";
sleep($argv[1]);
echo "two\n";
//throw new Exception("Something went wrong");
sleep($argv[1]);
echo "three\n";
sleep($argv[1]);
echo "Done!\n";
?>