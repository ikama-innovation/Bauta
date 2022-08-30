import embed from 'vega-embed';

window.renderGraph = function(data, startTime, endTime, jobOrStep, datePattern) {
    var durationString = startTime+" - "+endTime;
    var spec = {
        "$schema": "https://vega.github.io/schema/vega-lite/v5.json",
        "width": "container",
        "height": "container",
        "data": {
            "name": "jobData",
            "values": data
        },
        "mark": {
            "type":"bar",
            "cursor": "pointer",
            "tooltip": {"signal": "{'Name': datum.Name," +
                    "'Start': timeFormat(datum.Start, '%b %d %Y %-H:%M:%S'),"+
                    "'End': timeFormat(datum.End, '%b %d %Y %-H:%M:%S')," +
                    "'Status': datum.Status," +
                    "'Duration': datum.Duration}"},
        },
        "encoding": {
            "y": {"field": "Name", "type": "ordinal", "title": jobOrStep, "sort": null},
            "x": {"field": "Start", "type": "temporal", "axis": { "format":datePattern, "formatType":"time" }, "scale": {"domain": [startTime, endTime]}},
            "x2": {"field": "End", "title": durationString},
            "color": {
                "field": "Status",
                "scale": {
                    "domain": ["COMPLETED", "FAILED", "STARTED", "STOPPED", "UNKNOWN"],
                    "range": ["hsl(145, 100%, 32%)", "hsl(3, 100%, 61%)", "hsl(214, 90%, 52%)", "#b224bf", "gray"]
                },
            },
        },
        "config": {
            "background": "hsl(214, 35%, 21%)",
            "title": {"color": "#fff", "subtitleColor": "#fff"},
            "style": {"guide-label": {"fill": "#fff"}, "guide-title": {"fill": "#fff"}},
            "axis": {"domainColor": "#fff", "gridColor": "#888", "tickColor": "#fff"}
        }
    }
    console.log(data)
    console.log(startTime, endTime)
    embed("#vega", spec, { actions: false } );
};
