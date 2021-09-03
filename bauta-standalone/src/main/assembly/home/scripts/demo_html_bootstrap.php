<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/3.4.1/css/bootstrap.min.css" integrity="sha384-HSMxcRTRxnN+Bdg0JdbxYKrThecOKuH5zCYotlSAcp1+c8xmyTe9GYg1l9a69psu" crossorigin="anonymous">
<script src="https://cdn.jsdelivr.net/npm/chart.js@2.8.0"></script>
</head>
<body>
<div class="container">
<h1>My first PHP page</h1>
<p>A demo of how you could style the output of the PHP script using Bootstrap <a href="https://getbootstrap.com/docs/3.4/css/">css</a> and <a href="https://getbootstrap.com/docs/3.4/components/">components</a> </p>
<?php
echo "Hello World!";
?>
<ul>
<li>åäö</li>
<li>ÅÄÖ</li>
</ul>
<pre>
var hello="hello";
var world="world";
function demo() {
    printf(hello + workld);
}
</pre>
<pre>
one    two    three
four   five   six
abc    def    ghij
</pre>
 <table class="table">
  <tr>
    <th>Firstname</th>
    <th>Lastname</th>
    <th>Age</th>
  </tr>
  <tr>
    <td>Jill</td>
    <td>Smith</td>
    <td><span class="label label-success">Success</span></td>
  </tr>
  <tr>
    <td>Eve</td>
    <td>Jackson</td>
    <td><span class="label label-danger">Failed</span></td>
  </tr>
</table>
<br>
<div class="alert alert-success" role="alert"><span class="glyphicon glyphicon-thumbs-up" aria-hidden="true"></span> Something went fine </div>
<div class="alert alert-info" role="alert">Here is some info</div>
<div class="alert alert-warning" role="alert">And here is a warning</div>
<div class="alert alert-danger" role="alert"><span class="glyphicon glyphicon-thumbs-down" aria-hidden="true"></span> This is really bad </div>
<br>
<br>
<br>
<div class="progress">
  <div class="progress-bar" role="progressbar" aria-valuenow="60" aria-valuemin="0" aria-valuemax="100" style="width: 60%;">
    60%
  </div>
</div>
<div style="display: flex">
<canvas class="col-md-12" id="lineChart"  width="400" height="400" style="width: 400px;height: 400px;max-height: 400px;max-width: 400px"></canvas>
<canvas class="col-md-12" id="donutChart"  width="400" height="400" style="width: 400px;height: 400px;max-height: 400px;max-width: 400px"></canvas>
</div>
</div>
</body>
<script>
<?php
echo "var fromMongoDB=[0, 10, 5, 2, 20, 30, 45];";
echo "var fromMongoDB2 = {
          datasets: [{
              data: [10, 20, 30],
              backgroundColor: ['#5cb85c','#d9534f','#ffc107']

          }],
          labels: ['Cards','Racks','Cabinets']
      };";
?>
</script>
<script>
var ctx = document.getElementById('lineChart').getContext('2d');
var chart = new Chart(ctx, {
    // The type of chart we want to create
    type: 'line',

    // The data for our dataset
    data: {
        labels: ['January', 'February', 'March', 'April', 'May', 'June', 'July'],
        datasets: [{
            label: 'Progress',
            backgroundColor: '#5cb85c',
            borderColor: '#4ca84c',
            data: fromMongoDB
        }]
    },

    // Configuration options go here
    options: {}
});
var ctx2 = document.getElementById('donutChart').getContext('2d');
var myDoughnutChart = new Chart(ctx2, {
    type: 'doughnut',
    data: fromMongoDB2
});
</script>
</html> 